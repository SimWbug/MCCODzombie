package fr.cleangang.zombies.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Une fenêtre barricadée. Chaque bloc de l'ouverture est une planche.
 * Les zombies arrachent par le haut, les joueurs reposent par le bas.
 */
public final class Barricade {

    public final String id;
    public final String roomId;
    /** Blocs de l'ouverture, triés du bas vers le haut. */
    private final List<Block> cells;
    private final boolean[] present;
    private final Material plank;
    /** Côté intérieur (où se tient le joueur pour réparer). */
    public final Location insideAnchor;
    /** Côté extérieur (où se tient le zombie pour arracher). */
    public final Location outsideAnchor;
    /** Fond de l'enclos d'apparition. */
    public final Location spawn;
    private final Map<UUID, Integer> repairPointsThisRound = new HashMap<>();

    Barricade(String id, String roomId, List<Block> cells, Material plank, Location insideAnchor, Location outsideAnchor, Location spawn) {
        this.id = id;
        this.roomId = roomId;
        this.cells = List.copyOf(cells);
        this.present = new boolean[cells.size()];
        this.plank = plank;
        this.insideAnchor = insideAnchor;
        this.outsideAnchor = outsideAnchor;
        this.spawn = spawn;
    }

    public List<Block> cells() {
        return cells;
    }

    public int size() {
        return cells.size();
    }

    public int planks() {
        int n = 0;
        for (boolean b : present) if (b) n++;
        return n;
    }

    public boolean isOpen() {
        return planks() == 0;
    }

    public boolean isFull() {
        return planks() == cells.size();
    }

    /** Arrache la planche la plus haute. Renvoie false s'il n'y en a plus. */
    public boolean tear() {
        for (int i = cells.size() - 1; i >= 0; i--) {
            if (present[i]) {
                present[i] = false;
                cells.get(i).setType(Material.AIR, false);
                return true;
            }
        }
        return false;
    }

    /** Repose la planche manquante la plus basse. Renvoie false si la fenêtre est complète. */
    public boolean repair() {
        for (int i = 0; i < cells.size(); i++) {
            if (!present[i]) {
                present[i] = true;
                cells.get(i).setType(plank, false);
                return true;
            }
        }
        return false;
    }

    public void fill() {
        for (int i = 0; i < cells.size(); i++) {
            present[i] = true;
            cells.get(i).setType(plank, false);
        }
        repairPointsThisRound.clear();
    }

    /** Ajoute des points de réparation pour ce joueur en respectant le plafond par manche. Renvoie les points accordés. */
    public int grantRepairPoints(UUID player, int amount, int cap) {
        int already = repairPointsThisRound.getOrDefault(player, 0);
        int granted = Math.max(0, Math.min(amount, cap - already));
        if (granted > 0) repairPointsThisRound.put(player, already + granted);
        return granted;
    }

    public void resetRoundCaps() {
        repairPointsThisRound.clear();
    }
}
