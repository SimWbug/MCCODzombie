package fr.cleangang.zombies.map;

import org.bukkit.block.Block;

/** Position de bloc légère, utilisable comme clé de map. */
public record BlockPos(int x, int y, int z) {

    public static BlockPos of(Block b) {
        return new BlockPos(b.getX(), b.getY(), b.getZ());
    }
}
