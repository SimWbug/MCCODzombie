package fr.cleangang.zombies.map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vérifie une arène construite : points de départ valides et chemin praticable entre chaque enclos
 * d'apparition et les joueurs. Les planches comptent comme franchissables (les zombies les arrachent),
 * les débris de porte comme bloquants tant que la porte est fermée.
 */
public final class ArenaValidator {

    private static final int MAX_NODES = 250_000;

    private ArenaValidator() {
    }

    public static List<String> validate(BuiltArena arena) {
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < arena.playerSpawns.size(); i++) {
            Location s = arena.playerSpawns.get(i);
            if (!standable(arena, s.getBlock(), false)) {
                errors.add("Point de départ n°" + (i + 1) + " obstrué ou sans sol (" + fmt(s) + ")");
            }
        }
        if (!errors.isEmpty()) return errors;

        Set<BlockPos> targets = new HashSet<>();
        for (Location s : arena.playerSpawns) targets.add(BlockPos.of(s.getBlock()));

        // Passe 1 : portes fermées → chaque fenêtre de la salle de départ doit mener aux joueurs.
        for (Barricade b : arena.barricades) {
            if (!arena.isUnlocked(b.roomId)) continue;
            if (!reaches(arena, b.spawn.getBlock(), targets, false)) {
                errors.add("Fenêtre " + b.id + " : aucun chemin entre l'enclos et la salle de départ");
            }
        }
        // Passe 2 : portes ouvertes → toutes les fenêtres doivent mener aux joueurs.
        for (Barricade b : arena.barricades) {
            if (arena.isUnlocked(b.roomId)) continue;
            if (!reaches(arena, b.spawn.getBlock(), targets, true)) {
                errors.add("Fenêtre " + b.id + " (salle " + b.roomId + ") : inaccessible même portes ouvertes");
            }
        }
        if (arena.activeBarricades().isEmpty()) errors.add("Aucune fenêtre active au départ");
        return errors;
    }

    private static boolean reaches(BuiltArena arena, Block start, Set<BlockPos> targets, boolean doorsOpen) {
        if (!standable(arena, start, doorsOpen)) return false;
        World w = arena.world;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos first = BlockPos.of(start);
        queue.add(first);
        seen.add(first);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && seen.size() < MAX_NODES) {
            BlockPos p = queue.poll();
            if (targets.contains(p)) return true;
            for (int[] d : dirs) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos n = new BlockPos(p.x() + d[0], p.y() + dy, p.z() + d[1]);
                    if (seen.contains(n)) continue;
                    Block nb = w.getBlockAt(n.x(), n.y(), n.z());
                    if (!arena.bounds.clone().expand(1).contains(nb.getLocation().toVector())) continue;
                    // Monter d'un bloc demande de la place au-dessus de la tête
                    if (dy == 1 && !passable(arena, w.getBlockAt(p.x(), p.y() + 2, p.z()), doorsOpen)) continue;
                    if (standable(arena, nb, doorsOpen)) {
                        seen.add(n);
                        queue.add(n);
                        break;
                    }
                }
            }
        }
        return false;
    }

    /** Un zombie (2 blocs de haut) peut se tenir ici. */
    static boolean standable(BuiltArena arena, Block feet, boolean doorsOpen) {
        Block below = feet.getRelative(0, -1, 0);
        return below.getType().isSolid()
                && !arena.isBarricadeCell(below)
                && passable(arena, feet, doorsOpen)
                && passable(arena, feet.getRelative(0, 1, 0), doorsOpen);
    }

    private static boolean passable(BuiltArena arena, Block b, boolean doorsOpen) {
        if (arena.isBarricadeCell(b)) return true;
        DoorRuntime door = arena.doorAt(b);
        if (door != null) return doorsOpen || door.isOpen();
        return b.isPassable();
    }

    private static String fmt(Location l) {
        return String.format("%.1f %.1f %.1f", l.getX(), l.getY(), l.getZ());
    }
}
