package fr.cleangang.zombies.map;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Emplacement d'une arme murale dans l'arène construite.
 *
 * @param wall    bloc du mur (marqué) que le joueur vise pour acheter
 * @param display point juste devant le mur, orienté vers la salle, où l'arme est exposée
 */
public record WallBuySpot(MapDefinition.WallBuy def, Block wall, Location display) {
}
