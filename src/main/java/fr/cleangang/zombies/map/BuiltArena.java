package fr.cleangang.zombies.map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Une arène construite dans le monde, avec l'état de ses fenêtres et portes. */
public final class BuiltArena {

    public final MapDefinition def;
    public final World world;
    public final long seed;
    public final List<Barricade> barricades;
    public final List<DoorRuntime> doors;
    public final List<Location> playerSpawns;
    public final BoundingBox bounds;
    public final List<WallBuySpot> wallBuys;
    /** null si la carte n'a pas de boîte mystère. */
    public final BoxSpot box;
    private final Map<BlockPos, Barricade> barricadeCells;
    private final Map<BlockPos, DoorRuntime> doorCells;
    private final Set<String> unlockedRooms = new HashSet<>();

    BuiltArena(MapDefinition def, World world, long seed, List<Barricade> barricades, List<DoorRuntime> doors,
               List<Location> playerSpawns, BoundingBox bounds,
               Map<BlockPos, Barricade> barricadeCells, Map<BlockPos, DoorRuntime> doorCells,
               List<WallBuySpot> wallBuys, BoxSpot box) {
        this.def = def;
        this.world = world;
        this.seed = seed;
        this.barricades = Collections.unmodifiableList(barricades);
        this.doors = Collections.unmodifiableList(doors);
        this.playerSpawns = Collections.unmodifiableList(playerSpawns);
        this.bounds = bounds;
        this.barricadeCells = barricadeCells;
        this.doorCells = doorCells;
        this.wallBuys = Collections.unmodifiableList(wallBuys);
        this.box = box;
        resetState();
    }

    /** Remet portes et fenêtres dans leur état de départ (sans reconstruire les murs). */
    public void resetState() {
        unlockedRooms.clear();
        def.rooms.values().stream().filter(MapDefinition.Room::starting).forEach(r -> unlockedRooms.add(r.id()));
        for (DoorRuntime d : doors) d.close();
        for (Barricade b : barricades) b.fill();
    }

    public boolean isUnlocked(String roomId) {
        return unlockedRooms.contains(roomId);
    }

    public Set<String> unlockedRooms() {
        return Collections.unmodifiableSet(unlockedRooms);
    }

    /** Fenêtres dont la salle est accessible : seules celles-ci font apparaître des zombies. */
    public List<Barricade> activeBarricades() {
        List<Barricade> out = new ArrayList<>();
        for (Barricade b : barricades) if (unlockedRooms.contains(b.roomId)) out.add(b);
        return out;
    }

    public void openDoor(DoorRuntime door) {
        door.open();
        unlockedRooms.addAll(door.def.rooms());
    }

    public Barricade barricadeAt(Block b) {
        return b == null ? null : barricadeCells.get(BlockPos.of(b));
    }

    public boolean isBarricadeCell(Block b) {
        return barricadeAt(b) != null;
    }

    public DoorRuntime doorAt(Block b) {
        return b == null ? null : doorCells.get(BlockPos.of(b));
    }

    public WallBuySpot wallBuyAt(Block b) {
        if (b == null) return null;
        for (WallBuySpot w : wallBuys) if (w.wall().equals(b)) return w;
        return null;
    }

    public boolean isBox(Block b) {
        return b != null && box != null && box.chest().equals(b);
    }

    public boolean contains(Location loc, double margin) {
        return loc.getWorld() == world && bounds.clone().expand(margin).contains(loc.toVector());
    }

    public Location spawnFor(int index) {
        return playerSpawns.get(Math.floorMod(index, playerSpawns.size())).clone();
    }
}
