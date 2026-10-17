package fr.cleangang.zombies.util;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Un son configurable : clé (vanilla ou du pack), volume, pitch. */
public record SoundRef(String key, float volume, float pitch) {

    public static final SoundRef NONE = new SoundRef("", 0f, 1f);

    public static SoundRef parse(ConfigurationSection parent, String path, SoundRef fallback) {
        if (parent == null) return fallback;
        if (parent.isString(path)) {
            return new SoundRef(parent.getString(path, ""), 1f, 1f);
        }
        ConfigurationSection s = parent.getConfigurationSection(path);
        if (s == null) return fallback;
        return new SoundRef(s.getString("sound", ""), (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0));
    }

    public void play(Location at, SoundCategory category) {
        if (key.isEmpty() || at.getWorld() == null) return;
        at.getWorld().playSound(at, key, category, volume, pitch);
    }

    public void playTo(Player player) {
        if (key.isEmpty()) return;
        player.playSound(player.getLocation(), key, SoundCategory.MASTER, volume, pitch);
    }
}
