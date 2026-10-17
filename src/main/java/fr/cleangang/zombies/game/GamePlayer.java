package fr.cleangang.zombies.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/** État d'un joueur pendant une partie. */
public final class GamePlayer {

    public final UUID uuid;
    public final String name;
    public final int index;

    int points;
    int pointsEarned;
    int kills;
    int headshots;
    int downs;
    boolean alive = true;

    // Combat
    public int nextShotTick;
    public int nextMeleeTick;
    BukkitTask reloadTask;
    int reloadSlot = -1;

    // Réparation en cours
    int repairProgress;

    GamePlayer(Player p, int index) {
        this.uuid = p.getUniqueId();
        this.name = p.getName();
        this.index = index;
    }

    public Player player() {
        return Bukkit.getPlayer(uuid);
    }

    public int points() {
        return points;
    }

    public boolean alive() {
        return alive;
    }

    public boolean reloading() {
        return reloadTask != null && !reloadTask.isCancelled();
    }

    public void startReload(BukkitTask task, int slot) {
        cancelReload();
        reloadTask = task;
        reloadSlot = slot;
    }

    public int reloadSlot() {
        return reloadSlot;
    }

    public void finishReload() {
        reloadTask = null;
        reloadSlot = -1;
    }

    public void cancelReload() {
        if (reloadTask != null) reloadTask.cancel();
        finishReload();
    }
}
