package fr.cleangang.zombies.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Construit une carte à partir de son plan modulaire. La construction est déterministe :
 * la même graine donne exactement les mêmes variations de blocs et de décor.
 * <p>
 * Ordre : nettoyage → salles → enclos d'apparition → fenêtres → portes → éclairage → décor.
 */
public final class ArenaBuilder {

    /** Profondeur de l'enclos derrière chaque fenêtre (où apparaissent les zombies). */
    public static final int PEN_DEPTH = 4;
    private static final int CLEAR_MARGIN = PEN_DEPTH + 3;

    private final World world;
    private final int ox, oy, oz;

    public ArenaBuilder(World world, int ox, int oy, int oz) {
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    public BuiltArena build(MapDefinition def, long seed) {
        Random rng = new Random(seed ^ def.id.hashCode());
        MapDefinition.Theme theme = def.theme;

        MapDefinition.Box extent = extent(def);
        clear(extent);

        // 1. Salles
        for (MapDefinition.Room room : def.rooms.values()) {
            buildBox(room.box(), theme.floor(), theme.wall(), theme.ceiling(), rng, null);
        }

        // 2. Enclos + fenêtres
        List<Barricade> barricades = new ArrayList<>();
        Map<BlockPos, Barricade> barricadeCells = new HashMap<>();
        for (MapDefinition.Window w : def.windows) {
            Barricade b = buildWindow(def, w, rng);
            barricades.add(b);
            for (Block cell : b.cells()) barricadeCells.put(BlockPos.of(cell), b);
        }

        // 3. Portes
        List<DoorRuntime> doors = new ArrayList<>();
        Map<BlockPos, DoorRuntime> doorCells = new HashMap<>();
        for (MapDefinition.Door d : def.doors) {
            List<Block> cells = cells(d.box());
            DoorRuntime door = new DoorRuntime(d, cells, theme.door());
            door.close();
            doors.add(door);
            for (Block cell : cells) doorCells.put(BlockPos.of(cell), door);
        }

        // 4. Éclairage et toiles d'araignée (dépendent de la graine)
        for (MapDefinition.Room room : def.rooms.values()) decorateRoom(room.box(), theme, rng);

        // 5. Décor explicite
        for (MapDefinition.Decor d : def.decor) block(d.x(), d.y(), d.z()).setType(d.block(), false);

        // 6. Armes murales : bloc repère dans le mur, arme exposée juste devant
        List<WallBuySpot> wallBuys = new ArrayList<>();
        for (MapDefinition.WallBuy wb : def.wallBuys) {
            Block wall = block(wb.x(), wb.y(), wb.z());
            wall.setType(theme.wallBuy(), false);
            BlockFace f = wb.facing();
            Location display = wall.getLocation().add(0.5 + f.getModX() * 0.53, 0.5, 0.5 + f.getModZ() * 0.53);
            display.setDirection(new org.bukkit.util.Vector(f.getModX(), 0, f.getModZ()));
            wallBuys.add(new WallBuySpot(wb, wall, display));
        }

        // 7. Boîte mystère
        BoxSpot box = null;
        if (def.mysteryBox != null) {
            MapDefinition.MysteryBox mb = def.mysteryBox;
            Block chest = block(mb.x(), mb.y(), mb.z());
            BlockData data = Material.CHEST.createBlockData();
            if (data instanceof org.bukkit.block.data.type.Chest c) c.setFacing(mb.facing());
            chest.setBlockData(data, false);
            Location top = chest.getLocation().add(0.5, 1.0, 0.5);
            top.setDirection(new org.bukkit.util.Vector(mb.facing().getModX(), 0, mb.facing().getModZ()));
            box = new BoxSpot(mb, chest, top);
        }

        List<Location> spawns = new ArrayList<>();
        for (MapDefinition.Spawn s : def.playerSpawns) {
            spawns.add(new Location(world, ox + s.x(), oy + s.y(), oz + s.z(), s.yaw(), 0f));
        }

        BoundingBox bounds = new BoundingBox(ox + extent.minX(), oy + extent.minY(), oz + extent.minZ(),
                ox + extent.maxX() + 1, oy + extent.maxY() + 1, oz + extent.maxZ() + 1);
        return new BuiltArena(def, world, seed, barricades, doors, spawns, bounds, barricadeCells, doorCells, wallBuys, box);
    }

    /** Boîte englobant salles et enclos. */
    public static MapDefinition.Box extent(MapDefinition def) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (MapDefinition.Room r : def.rooms.values()) {
            MapDefinition.Box b = r.box();
            minX = Math.min(minX, b.minX());
            minY = Math.min(minY, b.minY());
            minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxX());
            maxY = Math.max(maxY, b.maxY());
            maxZ = Math.max(maxZ, b.maxZ());
        }
        if (minX == Integer.MAX_VALUE) return new MapDefinition.Box(0, 0, 0, 0, 0, 0);
        int m = PEN_DEPTH + 1;
        return new MapDefinition.Box(minX - m, minY, minZ - m, maxX + m, maxY, maxZ + m);
    }

    // ── Construction ─────────────────────────────────────────────

    private void clear(MapDefinition.Box e) {
        for (int x = e.minX() - CLEAR_MARGIN; x <= e.maxX() + CLEAR_MARGIN; x++) {
            for (int y = e.minY() - 2; y <= e.maxY() + 3; y++) {
                for (int z = e.minZ() - CLEAR_MARGIN; z <= e.maxZ() + CLEAR_MARGIN; z++) {
                    Block b = block(x, y, z);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
            }
        }
    }

    /** Sol, murs, plafond et intérieur vide. {@code skip} permet de ne pas toucher certains blocs. */
    private void buildBox(MapDefinition.Box box, List<Material> floor, List<Material> wall, List<Material> ceiling,
                          Random rng, CellFilter skip) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (skip != null && skip.test(x, y, z)) continue;
                    Material m;
                    if (y == box.minY()) m = pick(floor, rng);
                    else if (y == box.maxY()) m = pick(ceiling, rng);
                    else if (x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ()) m = pick(wall, rng);
                    else m = Material.AIR;
                    block(x, y, z).setType(m, false);
                }
            }
        }
    }

    private Barricade buildWindow(MapDefinition def, MapDefinition.Window w, Random rng) {
        MapDefinition.Box wb = w.box();
        MapDefinition.Room room = def.rooms.get(w.room());
        BlockFace f = w.facing();
        int nx = f.getModX(), nz = f.getModZ();
        int floorY = room.box().minY();
        int ceilY = floorY + 4;

        // Enclos : du mur (exclu) jusqu'à PEN_DEPTH+1 blocs vers l'extérieur, une colonne de plus de chaque côté.
        MapDefinition.Box pen;
        if (nz != 0) {
            int plane = wb.minZ();
            int far = plane + nz * (PEN_DEPTH + 1);
            pen = new MapDefinition.Box(wb.minX() - 1, floorY, Math.min(plane, far), wb.maxX() + 1, ceilY, Math.max(plane, far));
            buildBox(pen, def.theme.floor(), def.theme.penWall(), def.theme.penWall(), rng, (x, y, z) -> z == plane);
        } else {
            int plane = wb.minX();
            int far = plane + nx * (PEN_DEPTH + 1);
            pen = new MapDefinition.Box(Math.min(plane, far), floorY, wb.minZ() - 1, Math.max(plane, far), ceilY, wb.maxZ() + 1);
            buildBox(pen, def.theme.floor(), def.theme.penWall(), def.theme.penWall(), rng, (x, y, z) -> x == plane);
        }

        List<Block> cells = cells(wb);
        cells.sort(Comparator.comparingInt(Block::getY));

        double cx = ox + (wb.minX() + wb.maxX()) / 2.0 + 0.5;
        double cz = oz + (wb.minZ() + wb.maxZ()) / 2.0 + 0.5;
        double baseY = oy + wb.minY();
        Location inside = new Location(world, cx - nx * 1.5, baseY, cz - nz * 1.5);
        Location outside = new Location(world, cx + nx * 1.1, baseY, cz + nz * 1.1);
        Location spawn = new Location(world, cx + nx * PEN_DEPTH, baseY, cz + nz * PEN_DEPTH);
        // Les zombies regardent la fenêtre en apparaissant
        spawn.setDirection(inside.toVector().subtract(spawn.toVector()));

        Barricade b = new Barricade(w.id(), w.room(), cells, def.theme.plank(), inside, outside, spawn);
        b.fill();
        return b;
    }

    private void decorateRoom(MapDefinition.Box box, MapDefinition.Theme theme, Random rng) {
        int y = box.maxY() - 1;
        int spacing = theme.lightSpacing();
        int offset = spacing / 2;
        BlockData lantern = Material.LANTERN.createBlockData();
        if (lantern instanceof Lantern l) l.setHanging(true);
        for (int x = box.minX() + offset; x < box.maxX(); x += spacing) {
            for (int z = box.minZ() + offset; z < box.maxZ(); z += spacing) {
                Block b = block(x, y, z);
                if (b.getType().isAir()) b.setBlockData(lantern, false);
            }
        }
        int[][] corners = {
                {box.minX() + 1, box.minZ() + 1}, {box.maxX() - 1, box.minZ() + 1},
                {box.minX() + 1, box.maxZ() - 1}, {box.maxX() - 1, box.maxZ() - 1}};
        for (int[] c : corners) {
            if (rng.nextDouble() < theme.cobwebChance()) {
                Block b = block(c[0], y, c[1]);
                if (b.getType().isAir()) b.setType(Material.COBWEB, false);
            }
        }
    }

    private List<Block> cells(MapDefinition.Box box) {
        List<Block> out = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                    out.add(block(x, y, z));
        return out;
    }

    private Block block(int x, int y, int z) {
        return world.getBlockAt(ox + x, oy + y, oz + z);
    }

    private static Material pick(List<Material> options, Random rng) {
        return options.get(rng.nextInt(options.size()));
    }

    @FunctionalInterface
    private interface CellFilter {
        boolean test(int x, int y, int z);
    }
}
