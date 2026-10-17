package fr.cleangang.zombies.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sauvegarde sur disque l'état d'un joueur avant qu'il entre dans une partie (inventaire, position,
 * mode de jeu, vie, effets...). Le fichier est écrit AVANT de vider l'inventaire et supprimé après
 * restauration : si le serveur plante en pleine partie, le joueur récupère tout à sa prochaine connexion.
 */
public final class SnapshotStore {

    private final File folder;
    private final Logger log;

    public SnapshotStore(File folder, Logger log) {
        this.folder = folder;
        this.log = log;
        if (!folder.exists() && !folder.mkdirs()) log.warning("Impossible de créer " + folder);
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    public boolean has(UUID id) {
        return file(id).exists();
    }

    /** Renvoie false si la sauvegarde a échoué : dans ce cas, ne pas vider l'inventaire du joueur. */
    public boolean save(Player p) {
        if (has(p.getUniqueId())) return true; // une sauvegarde plus ancienne (crash) prime : on ne l'écrase pas
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", p.getName());
        y.set("location", p.getLocation());
        List<String> items = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            items.add(it == null || it.isEmpty() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        y.set("inventory", items);
        y.set("gamemode", p.getGameMode().name());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        y.set("max-health", max == null ? 20.0 : max.getBaseValue());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("saturation", (double) p.getSaturation());
        y.set("level", p.getLevel());
        y.set("exp", (double) p.getExp());
        y.set("effects", new ArrayList<>(p.getActivePotionEffects()));
        try {
            y.save(file(p.getUniqueId()));
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Sauvegarde impossible pour " + p.getName(), e);
            return false;
        }
    }

    /**
     * Restaure puis supprime la sauvegarde.
     *
     * @param teleport renvoyer le joueur à sa position d'origine
     * @return true si une sauvegarde existait
     */
    public boolean restore(Player p, boolean teleport) {
        File f = file(p.getUniqueId());
        if (!f.exists()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        p.getInventory().clear();
        List<String> items = y.getStringList("inventory");
        ItemStack[] contents = new ItemStack[p.getInventory().getContents().length];
        for (int i = 0; i < contents.length && i < items.size(); i++) {
            String raw = items.get(i);
            if (raw == null || raw.isEmpty()) continue;
            try {
                contents[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
            } catch (RuntimeException e) {
                log.warning("Objet illisible dans la sauvegarde de " + p.getName() + " (case " + i + ")");
            }
        }
        p.getInventory().setContents(contents);

        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        for (Object o : y.getList("effects", List.of())) {
            if (o instanceof PotionEffect e) p.addPotionEffect(e);
        }

        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) max.setBaseValue(y.getDouble("max-health", 20.0));
        double maxHealth = max == null ? 20.0 : max.getValue();
        p.setHealth(Math.max(1.0, Math.min(maxHealth, y.getDouble("health", maxHealth))));
        p.setFoodLevel(y.getInt("food", 20));
        p.setSaturation((float) y.getDouble("saturation", 5.0));
        p.setLevel(y.getInt("level", 0));
        p.setExp((float) Math.max(0, Math.min(1, y.getDouble("exp", 0))));
        p.setFireTicks(0);
        p.setFallDistance(0);

        GameMode gm;
        try {
            gm = GameMode.valueOf(y.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException e) {
            gm = GameMode.SURVIVAL;
        }
        p.setGameMode(gm);

        if (teleport) {
            Location loc = y.getLocation("location");
            if (loc != null && loc.getWorld() != null) p.teleport(loc);
        }
        if (!f.delete()) log.warning("Impossible de supprimer " + f);
        return true;
    }
}
