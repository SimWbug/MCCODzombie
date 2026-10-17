package fr.cleangang.zombies.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.List;

/** Une porte (débris) achetable pendant la partie. */
public final class DoorRuntime {

    public final MapDefinition.Door def;
    private final List<Block> cells;
    private final Material debris;
    private boolean open;

    DoorRuntime(MapDefinition.Door def, List<Block> cells, Material debris) {
        this.def = def;
        this.cells = List.copyOf(cells);
        this.debris = debris;
    }

    public boolean isOpen() {
        return open;
    }

    public List<Block> cells() {
        return cells;
    }

    public Location center() {
        Block first = cells.getFirst();
        Block last = cells.getLast();
        return first.getLocation().add(last.getLocation()).multiply(0.5).add(0.5, 0.5, 0.5);
    }

    void open() {
        open = true;
        for (Block b : cells) b.setType(Material.AIR, false);
    }

    void close() {
        open = false;
        for (Block b : cells) b.setType(debris, false);
    }
}
