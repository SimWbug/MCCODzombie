package fr.cleangang.zombies.map;

import fr.cleangang.zombies.config.Cfg;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan d'une carte, lu depuis maps/&lt;id&gt;.yml. Toutes les coordonnées sont relatives à l'origine de l'arène.
 * Le plan décrit des modules (salles) ; {@link ArenaBuilder} les assemble.
 */
public final class MapDefinition {

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Box of(int[] a, int[] b) {
            return new Box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
        }

        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    public record Room(String id, String name, Box box, boolean starting) {
    }

    public record Window(String id, String room, Box box, BlockFace facing) {
    }

    public record Door(String id, String name, int cost, Box box, List<String> rooms) {
    }

    public record Spawn(double x, double y, double z, float yaw) {
    }

    public record Decor(int x, int y, int z, Material block) {
    }

    public record Theme(List<Material> floor, List<Material> wall, List<Material> ceiling, List<Material> penWall,
                        Material plank, Material door, Material wallBuy, int lightSpacing, double cobwebChance) {
    }

    /** Arme murale : bloc du mur (pos) et direction vers l'intérieur de la salle. */
    public record WallBuy(String id, String weapon, String room, int x, int y, int z, BlockFace facing) {
    }

    /** Boîte mystère : bloc du coffre, côté de l'ouverture, prix. */
    public record MysteryBox(String room, int x, int y, int z, BlockFace facing, int cost) {
    }

    public final String id;
    public final int order;
    public final String displayName;
    public final Material icon;
    public final String difficulty;
    public final String players;
    public final List<String> description;
    public final boolean playable;
    public final Theme theme;
    public final Map<String, Room> rooms;
    public final List<Window> windows;
    public final List<Door> doors;
    public final List<Spawn> playerSpawns;
    public final List<Decor> decor;
    public final List<WallBuy> wallBuys;
    /** null si la carte n'a pas de boîte. */
    public final MysteryBox mysteryBox;

    private MapDefinition(String fileName, YamlConfiguration y, Cfg cfg) {
        String fallbackId = fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - 4) : fileName;
        id = y.getString("id", fallbackId);
        order = y.getInt("order", 99);
        displayName = y.getString("display-name", id);
        icon = cfg.material(y, "icon", Material.ZOMBIE_HEAD);
        difficulty = y.getString("difficulty", "?");
        players = y.getString("players", "1-4 joueurs");
        description = Collections.unmodifiableList(y.getStringList("description"));
        playable = y.getBoolean("playable", false);

        ConfigurationSection t = y.getConfigurationSection("theme");
        theme = new Theme(
                cfg.materials(t, "floor", Material.STONE_BRICKS),
                cfg.materials(t, "wall", Material.DEEPSLATE_BRICKS),
                cfg.materials(t, "ceiling", Material.DARK_OAK_PLANKS),
                cfg.materials(t, "pen-wall", Material.MUD_BRICKS),
                cfg.material(t, "plank", Material.OAK_PLANKS),
                cfg.material(t, "door", Material.DARK_OAK_LOG),
                cfg.material(t, "wall-buy", Material.POLISHED_BLACKSTONE),
                t == null ? 6 : Math.max(2, t.getInt("light-spacing", 6)),
                t == null ? 0.5 : t.getDouble("cobweb-chance", 0.5));

        Map<String, Room> roomMap = new LinkedHashMap<>();
        ConfigurationSection rs = y.getConfigurationSection("rooms");
        if (rs != null) {
            for (String rid : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(rid);
                if (r == null) continue;
                Box box = Box.of(cfg.vec3(r, "min"), cfg.vec3(r, "max"));
                if (box.sizeX() < 3 || box.sizeZ() < 3 || box.sizeY() < 4) {
                    cfg.error("rooms." + rid, "salle trop petite (min 3×4×3 murs compris)");
                }
                roomMap.put(rid, new Room(rid, r.getString("name", rid), box, r.getBoolean("starting", false)));
            }
        }
        rooms = Collections.unmodifiableMap(roomMap);

        List<Window> ws = new ArrayList<>();
        int i = 0;
        for (Map<?, ?> m : y.getMapList("windows")) {
            String where = "windows[" + i++ + "]";
            String wid = String.valueOf(m.get("id"));
            String room = String.valueOf(m.get("room"));
            Box box = Box.of(cfg.vec3(asList(m.get("min")), where + ".min"), cfg.vec3(asList(m.get("max")), where + ".max"));
            BlockFace face = cfg.horizontalFace(m.get("facing") == null ? null : String.valueOf(m.get("facing")), where + ".facing");
            ws.add(new Window(wid, room, box, face));
        }
        windows = Collections.unmodifiableList(ws);

        List<Door> ds = new ArrayList<>();
        i = 0;
        for (Map<?, ?> m : y.getMapList("doors")) {
            String where = "doors[" + i++ + "]";
            String did = String.valueOf(m.get("id"));
            Object costRaw = m.get("cost");
            int cost = costRaw instanceof Number n ? n.intValue() : 0;
            if (cost < 0) cfg.error(where + ".cost", "coût négatif");
            Box box = Box.of(cfg.vec3(asList(m.get("min")), where + ".min"), cfg.vec3(asList(m.get("max")), where + ".max"));
            List<String> rooms = new ArrayList<>();
            List<?> rr = asList(m.get("rooms"));
            if (rr != null) for (Object o : rr) rooms.add(String.valueOf(o));
            Object name = m.get("name");
            ds.add(new Door(did, name == null ? did : String.valueOf(name), cost, box, Collections.unmodifiableList(rooms)));
        }
        doors = Collections.unmodifiableList(ds);

        List<Spawn> sp = new ArrayList<>();
        i = 0;
        for (Object o : y.getList("player-spawns", List.of())) {
            List<?> l = asList(o);
            if (l == null || l.size() < 3) {
                cfg.error("player-spawns[" + i + "]", "attendu [x, y, z, orientation]");
            } else {
                sp.add(new Spawn(num(l.get(0)), num(l.get(1)), num(l.get(2)), l.size() > 3 ? (float) num(l.get(3)) : 0f));
            }
            i++;
        }
        playerSpawns = Collections.unmodifiableList(sp);

        List<Decor> dec = new ArrayList<>();
        i = 0;
        for (Map<?, ?> m : y.getMapList("decor")) {
            String where = "decor[" + i++ + "]";
            int[] p = cfg.vec3(asList(m.get("pos")), where + ".pos");
            Material mat = cfg.materialOf(m.get("block") == null ? null : String.valueOf(m.get("block")), where + ".block", null);
            if (mat != null) dec.add(new Decor(p[0], p[1], p[2], mat));
        }
        decor = Collections.unmodifiableList(dec);

        List<WallBuy> wb = new ArrayList<>();
        i = 0;
        for (Map<?, ?> m : y.getMapList("wall-buys")) {
            String where = "wall-buys[" + i++ + "]";
            int[] p = cfg.vec3(asList(m.get("pos")), where + ".pos");
            BlockFace face = cfg.horizontalFace(m.get("facing") == null ? null : String.valueOf(m.get("facing")), where + ".facing");
            wb.add(new WallBuy(String.valueOf(m.get("id")), String.valueOf(m.get("weapon")), String.valueOf(m.get("room")),
                    p[0], p[1], p[2], face));
        }
        wallBuys = Collections.unmodifiableList(wb);

        ConfigurationSection mb = y.getConfigurationSection("mystery-box");
        if (mb != null) {
            int[] p = cfg.vec3(mb, "pos");
            mysteryBox = new MysteryBox(mb.getString("room", ""), p[0], p[1], p[2],
                    cfg.horizontalFace(mb.getString("facing"), "mystery-box.facing"),
                    cfg.integer(mb, "cost", 950, 0, 1_000_000));
        } else {
            mysteryBox = null;
        }

        if (playable) validateLayout(cfg);
    }

    /** Vérifications statiques du plan (avant toute construction). */
    private void validateLayout(Cfg cfg) {
        if (rooms.isEmpty()) cfg.error("rooms", "aucune salle");
        if (rooms.values().stream().noneMatch(Room::starting)) cfg.error("rooms", "aucune salle avec starting: true");
        if (playerSpawns.isEmpty()) cfg.error("player-spawns", "aucun point de départ");
        for (Window w : windows) {
            String where = "windows." + w.id();
            Room room = rooms.get(w.room());
            if (room == null) {
                cfg.error(where, "salle inconnue « " + w.room() + " »");
                continue;
            }
            boolean flatZ = w.box().sizeZ() == 1 && (w.facing() == BlockFace.NORTH || w.facing() == BlockFace.SOUTH);
            boolean flatX = w.box().sizeX() == 1 && (w.facing() == BlockFace.EAST || w.facing() == BlockFace.WEST);
            if (!flatZ && !flatX) cfg.error(where, "la fenêtre doit être plate dans le mur correspondant à facing");
            if (w.box().sizeY() < 2) cfg.error(where, "hauteur minimale 2 blocs");
            int plane = flatZ ? w.box().minZ() : w.box().minX();
            int expected = switch (w.facing()) {
                case NORTH -> room.box().minZ();
                case SOUTH -> room.box().maxZ();
                case WEST -> room.box().minX();
                default -> room.box().maxX();
            };
            if (plane != expected) cfg.error(where, "n'est pas dans le mur " + w.facing() + " de la salle " + room.id());
            if (w.box().minY() != room.box().minY() + 1) cfg.error(where, "doit commencer au niveau du sol de la salle (y = sol + 1)");
        }
        for (Door d : doors) {
            if (d.rooms().size() != 2) cfg.error("doors." + d.id(), "rooms doit lister exactement 2 salles");
            for (String r : d.rooms()) if (!rooms.containsKey(r)) cfg.error("doors." + d.id(), "salle inconnue « " + r + " »");
        }
        for (WallBuy w : wallBuys) {
            if (!rooms.containsKey(w.room())) cfg.error("wall-buys." + w.id(), "salle inconnue « " + w.room() + " »");
        }
        if (mysteryBox != null && !rooms.containsKey(mysteryBox.room())) {
            cfg.error("mystery-box.room", "salle inconnue « " + mysteryBox.room() + " »");
        }
        boolean startHasWindow = windows.stream().anyMatch(w -> rooms.containsKey(w.room()) && rooms.get(w.room()).starting());
        if (!startHasWindow) cfg.error("windows", "aucune fenêtre dans la salle de départ : les zombies ne pourraient pas entrer");
    }

    public static MapDefinition load(File file, List<String> errors) {
        Cfg cfg = new Cfg("maps/" + file.getName(), errors);
        return new MapDefinition(file.getName(), YamlConfiguration.loadConfiguration(file), cfg);
    }

    private static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : null;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
