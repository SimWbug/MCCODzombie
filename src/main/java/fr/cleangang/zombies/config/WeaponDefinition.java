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
import java.util.Locale;
import java.util.Map;

/** Une arme de weapons.yml. */
public record WeaponDefinition(
        String id,
        Type type,
        String name,
        List<String> lore,
        Material material,
        NamespacedKey itemModel,
        int cost,
        int ammoCost,
        int boxWeight,
        double damage,
        double headshotMultiplier,
        int fireDelayTicks,
        int magazine,
        int reserve,
        int reloadTicks,
        double range,
        double spread,
        int pellets,
        double splashRadius,
        double splashDamage,
        double meleeDamage,
        SoundRef fireSound,
        SoundRef reloadSound,
        SoundRef emptySound,
        SoundRef lastShotSound
) {

    /** GUN : balle instantanée · RAYGUN : rayon à éclaboussure · MELEE : améliore le couteau (clic gauche). */
    public enum Type {GUN, RAYGUN, MELEE}

    public boolean isFirearm() {
        return type != Type.MELEE;
    }

    public static Map<String, WeaponDefinition> loadAll(File file, List<String> errors) {
        Cfg cfg = new Cfg("weapons.yml", errors);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        Map<String, WeaponDefinition> out = new LinkedHashMap<>();
        ConfigurationSection root = cfg.section(y, "weapons");
        if (root == null) return out;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            String where = s.getCurrentPath();

            Type type;
            try {
                type = Type.valueOf(s.getString("type", "GUN").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                cfg.error(where + ".type", "doit valoir GUN, RAYGUN ou MELEE");
                type = Type.GUN;
            }
            NamespacedKey model = null;
            String rawModel = s.getString("item-model", "");
            if (!rawModel.isBlank()) {
                model = NamespacedKey.fromString(rawModel.trim());
                if (model == null) cfg.error(where + ".item-model", "clé invalide « " + rawModel + " »");
            }
            int cost = cfg.integer(s, "cost", 0, 0, 1_000_000);
            ConfigurationSection sounds = s.getConfigurationSection("sounds");
            out.put(id, new WeaponDefinition(
                    id,
                    type,
                    s.getString("name", id),
                    Collections.unmodifiableList(s.getStringList("lore")),
                    cfg.material(s, "material", Material.IRON_HORSE_ARMOR),
                    model,
                    cost,
                    cfg.integer(s, "ammo-cost", cost / 2, 0, 1_000_000),
                    cfg.integer(s, "box-weight", 0, 0, 10_000),
                    cfg.number(s, "damage", 4, 0, 1_000_000),
                    cfg.number(s, "headshot-multiplier", 2, 1, 100),
                    cfg.integer(s, "fire-delay-ticks", 5, 1, 200),
                    cfg.integer(s, "magazine", 8, 1, 10_000),
                    cfg.integer(s, "reserve", 40, 0, 100_000),
                    cfg.integer(s, "reload-ticks", 30, 1, 400),
                    cfg.number(s, "range", 48, 2, 128),
                    cfg.number(s, "spread", 0, 0, 1),
                    cfg.integer(s, "pellets", 1, 1, 32),
                    cfg.number(s, "splash-radius", 0, 0, 16),
                    cfg.number(s, "splash-damage", 0, 0, 1_000_000),
                    cfg.number(s, "melee-damage", 0, 0, 1_000_000),
                    SoundRef.parse(sounds, "fire", SoundRef.NONE),
                    SoundRef.parse(sounds, "reload", SoundRef.NONE),
                    SoundRef.parse(sounds, "empty", SoundRef.NONE),
                    SoundRef.parse(sounds, "last-shot", SoundRef.NONE)));
            if (type == Type.MELEE && out.get(id).meleeDamage() <= 0) cfg.error(where + ".melee-damage", "obligatoire pour une arme MELEE");
            if (type == Type.RAYGUN && out.get(id).splashRadius() <= 0) cfg.error(where + ".splash-radius", "obligatoire pour une arme RAYGUN");
        }
        if (out.isEmpty()) cfg.error("weapons", "aucune arme définie");
        return Collections.unmodifiableMap(out);
    }
}
