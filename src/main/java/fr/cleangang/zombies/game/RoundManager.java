package fr.cleangang.zombies.game;

import fr.cleangang.zombies.config.PluginSettings;

/**
 * Manches : file d'apparition, zombies restants, pause entre les manches.
 * La manche suivante ne démarre qu'une fois tous les zombies de la manche éliminés.
 */
public final class RoundManager {

    private final Game game;
    private final PluginSettings.Rounds cfg;

    private int round;
    private int toSpawn;
    private int spawnCooldown;
    private int intermissionLeft = -1;

    RoundManager(Game game, PluginSettings.Rounds cfg) {
        this.game = game;
        this.cfg = cfg;
    }

    public int round() {
        return round;
    }

    public boolean inIntermission() {
        return intermissionLeft >= 0;
    }

    /** Zombies restants dans la manche : ceux à venir + ceux en vie. */
    public int remaining() {
        return toSpawn + game.zombies().alive();
    }

    void startRound(int r) {
        round = r;
        intermissionLeft = -1;
        toSpawn = cfg.zombieCount(r, game.playerCount());
        spawnCooldown = 40;
        game.onRoundStart(r);
    }

    /** Remet des zombies dans la file (zombie bloqué ou sorti de la carte). */
    void requeue(int n) {
        toSpawn += n;
    }

    void tick() {
        if (intermissionLeft >= 0) {
            if (--intermissionLeft <= 0) startRound(round + 1);
            return;
        }
        if (toSpawn > 0 && --spawnCooldown <= 0) {
            spawnCooldown = cfg.spawnInterval(round);
            if (game.zombies().alive() < cfg.maxAlive(game.playerCount()) && game.zombies().spawnOne(round)) {
                toSpawn--;
            }
        }
        if (toSpawn == 0 && game.zombies().alive() == 0) {
            if (cfg.maxRound > 0 && round >= cfg.maxRound) {
                game.onVictory();
            } else {
                intermissionLeft = Math.max(1, cfg.intermissionSeconds * 20);
                game.onRoundEnd(round);
            }
        }
    }
}
