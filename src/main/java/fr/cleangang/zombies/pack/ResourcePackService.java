package fr.cleangang.zombies.pack;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.config.PluginSettings;
import fr.cleangang.zombies.util.Msg;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Envoie le pack du mode Zombies au moment où le joueur entre dans une partie (pas à la connexion au serveur),
 * et le retire à la sortie. Pack obligatoire : refus ou échec = retour au hub avec un message clair.
 */
public final class ResourcePackService implements Listener {

    private record Pending(Runnable ok, Consumer<String> fail, BukkitTask timeout) {
    }

    private final ZombiesPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private boolean warnedDisabled;

    public ResourcePackService(ZombiesPlugin plugin) {
        this.plugin = plugin;
    }

    private PluginSettings.Pack cfg() {
        return plugin.settings().pack;
    }

    private UUID packId() {
        return UUID.nameUUIDFromBytes(("cleanzombies:" + cfg().url).getBytes(StandardCharsets.UTF_8));
    }

    public void request(Player p, Runnable ok, Consumer<String> fail) {
        PluginSettings.Pack c = cfg();
        if (!c.enabled()) {
            if (!warnedDisabled) {
                plugin.getLogger().warning("resource-pack.url est vide : les joueurs entrent sans pack (armes et sons personnalisés invisibles).");
                warnedDisabled = true;
            }
            ok.run();
            return;
        }
        cancel(p);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending pd = pending.remove(p.getUniqueId());
            if (pd != null) pd.fail().accept("délai dépassé");
        }, c.timeoutSeconds * 20L);
        pending.put(p.getUniqueId(), new Pending(ok, fail, timeout));

        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId(), URI.create(c.url), c.sha1);
        ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(c.required)
                .prompt(Msg.mm(c.prompt))
                .replace(false)
                .build();
        p.sendResourcePacks(req);
    }

    public void unload(Player p) {
        cancel(p);
        PluginSettings.Pack c = cfg();
        if (c.enabled() && c.unloadOnLeave && p.isOnline()) p.removeResourcePacks(packId());
    }

    private void cancel(Player p) {
        Pending pd = pending.remove(p.getUniqueId());
        if (pd != null) pd.timeout().cancel();
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (!e.getID().equals(packId())) return;
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> resolve(p, null);
            case DECLINED -> resolve(p, "pack refusé");
            case FAILED_DOWNLOAD -> resolve(p, "téléchargement échoué");
            case INVALID_URL -> resolve(p, "adresse du pack invalide");
            case FAILED_RELOAD -> resolve(p, "rechargement échoué");
            case DISCARDED -> resolve(p, "pack ignoré");
            default -> {
                // ACCEPTED / DOWNLOADED : étapes intermédiaires
            }
        }
    }

    private void resolve(Player p, String failure) {
        Pending pd = pending.remove(p.getUniqueId());
        if (pd == null) return;
        pd.timeout().cancel();
        if (failure == null || !cfg().required) pd.ok().run();
        else pd.fail().accept(failure);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancel(e.getPlayer());
    }
}
