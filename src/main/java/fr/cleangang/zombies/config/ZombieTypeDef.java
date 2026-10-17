package fr.cleangang.zombies.config;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

/** Une variante de zombie (standard, rapide, renforcé...). */
public record ZombieTypeDef(
        String id,
        int minRound,
        double chanceBase,
        double chancePerRound,
        double chanceMax,
        double speed,
        double damage,
        double healthMultiplier,
        double knockbackResistance,
        Color armorColor
) {

    static ZombieTypeDef parse(String id, ConfigurationSection s, Cfg cfg) {
        if (s == null) {
            cfg.error("zombie-types." + id, "section vide");
            return new ZombieTypeDef(id, 1, 0, 0, 0, 0.23, 7, 1, 0.4, Color.GRAY);
        }
        Color color;
        String hex = s.getString("color", "4B5320");
        try {
            color = Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            cfg.error(s.getCurrentPath() + ".color", "couleur hexadécimale invalide « " + hex + " »");
            color = Color.GRAY;
        }
        return new ZombieTypeDef(
                id,
                cfg.integer(s, "min-round", 1, 1, 100_000),
                cfg.number(s, "chance-base", 0, 0, 1),
                cfg.number(s, "chance-per-round", 0, 0, 1),
                cfg.number(s, "chance-max", 1, 0, 1),
                cfg.number(s, "speed", 0.23, 0.01, 2),
                cfg.number(s, "damage", 7, 0, 1000),
                cfg.number(s, "health-multiplier", 1, 0.05, 1000),
                cfg.number(s, "knockback-resistance", 0.4, 0, 1),
                color);
    }

    /** Probabilité d'apparition à cette manche (0 avant min-round). */
    public double chanceAt(int round) {
        if (round < minRound) return 0;
        return Math.min(chanceMax, chanceBase + chancePerRound * (round - minRound));
    }
}
