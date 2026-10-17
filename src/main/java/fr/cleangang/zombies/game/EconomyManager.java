package fr.cleangang.zombies.game;

/**
 * Points des joueurs. Tout passe par le thread principal du serveur et chaque achat
 * vérifie puis débite en une seule opération : un double clic ne peut pas payer deux fois
 * ni descendre sous zéro.
 */
public final class EconomyManager {

    public void reset(GamePlayer gp, int start) {
        gp.points = start;
        gp.pointsEarned = 0;
    }

    public void reward(GamePlayer gp, int amount) {
        if (amount <= 0) return;
        gp.points += amount;
        gp.pointsEarned += amount;
    }

    /** Débite si possible. Renvoie false sans rien changer si le solde est insuffisant. */
    public synchronized boolean spend(GamePlayer gp, int cost) {
        if (cost < 0 || gp.points < cost) return false;
        gp.points -= cost;
        return true;
    }
}
