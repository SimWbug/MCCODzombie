package fr.cleangang.zombies.map;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Emplacement de la boîte mystère.
 *
 * @param chest bloc du coffre
 * @param top   centre du dessus du coffre (où l'arme tourne)
 */
public record BoxSpot(MapDefinition.MysteryBox def, Block chest, Location top) {
}
