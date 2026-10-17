package fr.cleangang.zombies.config;

import fr.cleangang.zombies.util.SoundRef;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Une arme de weapons.yml. */
public record WeaponDefinition(
        String id,
        String name,
        List<String> lore,
        Material material,
        NamespacedKey itemModel,
        double damage,
        double headshotMultiplier,
        int fireDelayTicks,
        int magazine,
        int reserve,
        int reloadTicks,
        double range,
        double spread,
        int pellets,
        SoundRef fireSound,
        SoundRef reloadSound,
        SoundRef emptySound
) {

    public static Map<String, WeaponDefinition> loadAll(File file, List<String> errors) {
        Cfg cfg = new Cfg("weapons.yml", errors);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        Map<String, WeaponDefinition> out = new LinkedHashMap<>();
        ConfigurationSection root = cfg.section(y, "weapons");
        if (root == null) return out;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            NamespacedKey model = null;
            String rawModel = s.getString("item-model", "");
            if (!rawModel.isBlank()) {
                model = NamespacedKey.fromString(rawModel.trim());
                if (model == null) cfg.error(s.getCurrentPath() + ".item-model", "clé invalide « " + rawModel + " »");
            }
            ConfigurationSection sounds = s.getConfigurationSection("sounds");
            out.put(id, new WeaponDefinition(
                    id,
                    s.getString("name", id),
                    Collections.unmodifiableList(s.getStringList("lore")),
                    cfg.material(s, "material", Material.IRON_HORSE_ARMOR),
                    model,
                    cfg.number(s, "damage", 4, 0, 1_000_000),
                    cfg.number(s, "headshot-multiplier", 2, 1, 100),
                    cfg.integer(s, "fire-delay-ticks", 5, 1, 200),
                    cfg.integer(s, "magazine", 8, 1, 10_000),
                    cfg.integer(s, "reserve", 40, 0, 100_000),
                    cfg.integer(s, "reload-ticks", 30, 1, 400),
                    cfg.number(s, "range", 48, 2, 128),
                    cfg.number(s, "spread", 0, 0, 1),
                    cfg.integer(s, "pellets", 1, 1, 32),
                    SoundRef.parse(sounds, "fire", SoundRef.NONE),
                    SoundRef.parse(sounds, "reload", SoundRef.NONE),
                    SoundRef.parse(sounds, "empty", SoundRef.NONE)));
        }
        if (out.isEmpty()) cfg.error("weapons", "aucune arme définie");
        return Collections.unmodifiableMap(out);
    }
}
