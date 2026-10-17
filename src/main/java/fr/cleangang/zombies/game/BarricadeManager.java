package fr.cleangang.zombies.game;

import fr.cleangang.zombies.config.PluginSettings;
import fr.cleangang.zombies.map.Barricade;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Réparation des fenêtres : un joueur vivant, accroupi près de l'intérieur d'une fenêtre abîmée,
 * repose une planche toutes les {@code repair-ticks}. Chaque planche rapporte des points dans la limite
 * de {@code repair-cap-per-window} par fenêtre et par manche (anti-farm).
 */
public final class BarricadeManager {

    private static final int PERIOD = 2;

    private final Game game;
    private final PluginSettings s;

    BarricadeManager(Game game, PluginSettings s) {
        this.game = game;
        this.s = s;
    }

    void tick() {
        if (game.tick() % PERIOD != 0) return;
        for (GamePlayer gp : game.players()) {
            Player p = gp.player();
            if (p == null || !gp.alive || !p.isSneaking()) {
                gp.repairProgress = 0;
                continue;
            }
            Barricade b = repairableNear(p);
            if (b == null) {
                gp.repairProgress = 0;
                continue;
            }
            gp.repairProgress += PERIOD;
            if (gp.repairProgress < s.repairTicks) continue;
            gp.repairProgress = 0;
            if (b.repair()) {
                s.sound("repair").play(b.insideAnchor, SoundCategory.BLOCKS);
                int pts = b.grantRepairPoints(gp.uuid, s.points.repair, s.points.repairCapPerWindow);
                game.economy().reward(gp, pts);
            }
        }
    }

    /** Fenêtre abîmée la plus proche à portée de réparation, ou null. */
    public Barricade repairableNear(Player p) {
        double range2 = s.repairRange * s.repairRange;
        Barricade best = null;
        double bestDist = Double.MAX_VALUE;
        for (Barricade b : game.arena().barricades) {
            if (b.isFull() || b.insideAnchor.getWorld() != p.getWorld()) continue;
            double d = b.insideAnchor.distanceSquared(p.getLocation());
            if (d <= range2 && d < bestDist) {
                best = b;
                bestDist = d;
            }
        }
        return best;
    }
}
