package fr.cleangang.zombies.config;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lecture de configuration qui accumule des erreurs lisibles au lieu de planter.
 * Chaque erreur indique le fichier et le chemin fautif.
 */
public final class Cfg {

    private final String file;
    private final List<String> errors;

    public Cfg(String file, List<String> errors) {
        this.file = file;
        this.errors = errors;
    }

    public void error(String path, String message) {
        errors.add(file + " → " + path + " : " + message);
    }

    public List<String> errors() {
        return errors;
    }

    public ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection s = parent.getConfigurationSection(path);
        if (s == null) error(full(parent, path), "section manquante");
        return s;
    }

    public int integer(ConfigurationSection s, String path, int def, int min, int max) {
        if (s == null) return def;
        int v = s.getInt(path, def);
        if (v < min || v > max) {
            error(full(s, path), "doit être entre " + min + " et " + max + " (lu : " + v + ")");
            return def;
        }
        return v;
    }

    public double number(ConfigurationSection s, String path, double def, double min, double max) {
        if (s == null) return def;
        double v = s.getDouble(path, def);
        if (Double.isNaN(v) || v < min || v > max) {
            error(full(s, path), "doit être entre " + min + " et " + max + " (lu : " + v + ")");
            return def;
        }
        return v;
    }

    public Material material(ConfigurationSection s, String path, Material def) {
        if (s == null || !s.contains(path)) return def;
        return materialOf(s.getString(path), full(s, path), def);
    }

    public Material materialOf(String raw, String where, Material def) {
        if (raw == null) return def;
        Material m = Material.matchMaterial(raw.trim());
        if (m == null) {
            error(where, "bloc/objet inconnu « " + raw + " »");
            return def;
        }
        return m;
    }

    public List<Material> materials(ConfigurationSection s, String path, Material def) {
        List<Material> out = new ArrayList<>();
        if (s == null || !s.contains(path)) {
            out.add(def);
            return out;
        }
        if (s.isString(path)) {
            out.add(material(s, path, def));
            return out;
        }
        for (String raw : s.getStringList(path)) {
            Material m = materialOf(raw, full(s, path), null);
            if (m != null) out.add(m);
        }
        if (out.isEmpty()) out.add(def);
        return out;
    }

    public int[] vec3(ConfigurationSection s, String path) {
        List<Integer> list = s.getIntegerList(path);
        if (list.size() != 3) {
            error(full(s, path), "attendu [x, y, z]");
            return new int[]{0, 0, 0};
        }
        return new int[]{list.get(0), list.get(1), list.get(2)};
    }

    public int[] vec3(List<?> raw, String where) {
        if (raw == null || raw.size() != 3) {
            error(where, "attendu [x, y, z]");
            return new int[]{0, 0, 0};
        }
        int[] v = new int[3];
        for (int i = 0; i < 3; i++) {
            Object o = raw.get(i);
            if (o instanceof Number n) {
                v[i] = n.intValue();
            } else {
                error(where, "coordonnée non numérique « " + o + " »");
            }
        }
        return v;
    }

    public BlockFace horizontalFace(String raw, String where) {
        if (raw != null) {
            try {
                BlockFace f = BlockFace.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                if (f == BlockFace.NORTH || f == BlockFace.SOUTH || f == BlockFace.EAST || f == BlockFace.WEST) return f;
            } catch (IllegalArgumentException ignored) {
                // erreur signalée plus bas
            }
        }
        error(where, "facing doit valoir NORTH, SOUTH, EAST ou WEST (lu : " + raw + ")");
        return BlockFace.NORTH;
    }

    private static String full(ConfigurationSection s, String path) {
        String base = s == null ? "" : s.getCurrentPath();
        return base == null || base.isEmpty() ? path : base + "." + path;
    }
}
