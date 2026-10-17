package fr.cleangang.zombies;

import fr.cleangang.zombies.command.ZombiesCommand;
import fr.cleangang.zombies.config.PluginSettings;
import fr.cleangang.zombies.config.WeaponDefinition;
import fr.cleangang.zombies.game.GameManager;
import fr.cleangang.zombies.game.SnapshotStore;
import fr.cleangang.zombies.listener.GameListener;
import fr.cleangang.zombies.listener.MenuListener;
import fr.cleangang.zombies.map.MapRegistry;
import fr.cleangang.zombies.pack.ResourcePackService;
import fr.cleangang.zombies.weapon.WeaponService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CleanZombies — mode Zombies coopératif par manches.
 * Étape 1 : prototype jouable sur Bunker 13 (manches, zombies, fenêtres, portes, points, pistolet, défaite).
 */
public final class ZombiesPlugin extends JavaPlugin {

    private static final List<String> BUNDLED_MAPS = List.of("bunker", "sanatorium", "marais", "titan");

    private PluginSettings settings;
    private Map<String, WeaponDefinition> weapons;
    private MapRegistry maps;
    private Keys keys;
    private SnapshotStore snapshots;
    private ResourcePackService packs;
    private WeaponService weaponService;
    private GameManager games;
    private boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("weapons.yml");
        for (String m : BUNDLED_MAPS) saveIfMissing("maps/" + m + ".yml");

        keys = new Keys(this);
        snapshots = new SnapshotStore(new File(getDataFolder(), "snapshots"), getLogger());

        List<String> errors = reloadData();
        if (!errors.isEmpty()) {
            getLogger().severe("Configuration invalide (" + errors.size() + " erreur(s)) — le mode Zombies est désactivé jusqu'à correction :");
            errors.forEach(e -> getLogger().severe("  • " + e));
            getLogger().severe("Corrige les fichiers puis fais /zombies reload.");
        }

        packs = new ResourcePackService(this);
        weaponService = new WeaponService(this);
        games = new GameManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(packs, this);

        PluginCommand cmd = getCommand("zombies");
        if (cmd != null) {
            ZombiesCommand executor = new ZombiesCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        if (ready) {
            games.start();
            games.arenaWorld(); // crée le monde dès le démarrage plutôt qu'au premier joueur
        }
    }

    @Override
    public void onDisable() {
        if (games != null) games.shutdown();
    }

    /**
     * Relit config.yml, weapons.yml et maps/. En cas d'erreur, l'ancienne configuration reste active.
     *
     * @return la liste des erreurs (vide si tout est valide)
     */
    public List<String> reloadData() {
        List<String> errors = new ArrayList<>();
        reloadConfig();
        PluginSettings newSettings = PluginSettings.load(getConfig(), errors);
        Map<String, WeaponDefinition> newWeapons = WeaponDefinition.loadAll(new File(getDataFolder(), "weapons.yml"), errors);
        MapRegistry newMaps = MapRegistry.load(new File(getDataFolder(), "maps"), errors);
        if (!newWeapons.containsKey(newSettings.startingWeapon)) {
            errors.add("config.yml → player.starting-weapon : arme « " + newSettings.startingWeapon + " » absente de weapons.yml");
        }
        if (newMaps.get(newSettings.defaultMap) == null) {
            errors.add("config.yml → lobby.default-map : carte « " + newSettings.defaultMap + " » introuvable");
        }
        if (errors.isEmpty()) {
            boolean firstLoad = !ready;
            settings = newSettings;
            weapons = newWeapons;
            maps = newMaps;
            ready = true;
            if (firstLoad && games != null) {
                games.start();
                games.arenaWorld();
            }
        }
        return errors;
    }

    private void saveIfMissing(String path) {
        if (!new File(getDataFolder(), path).exists()) saveResource(path, false);
    }

    // ── Accès ────────────────────────────────────────────────────

    public boolean isReady() {
        return ready;
    }

    public PluginSettings settings() {
        return settings;
    }

    public Map<String, WeaponDefinition> weapons() {
        return weapons;
    }

    public MapRegistry maps() {
        return maps;
    }

    public Keys keys() {
        return keys;
    }

    public SnapshotStore snapshots() {
        return snapshots;
    }

    public ResourcePackService packs() {
        return packs;
    }

    public WeaponService weaponService() {
        return weaponService;
    }

    public GameManager games() {
        return games;
    }

}
