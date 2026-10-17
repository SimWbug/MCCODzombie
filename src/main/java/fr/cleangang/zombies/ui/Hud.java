package fr.cleangang.zombies.ui;

import fr.cleangang.zombies.util.Msg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/** Tableau latéral propre à une partie (manche, zombies restants, points de chaque joueur). */
public final class Hud {

    private final Scoreboard board;
    private final Objective objective;
    private List<Component> current = new ArrayList<>();

    public Hud() {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("cgzombies", Criteria.DUMMY, Msg.mm("<dark_red><bold>☠ ZOMBIES ☠"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
    }

    public void attach(Player p) {
        p.setScoreboard(board);
    }

    public void detach(Player p) {
        if (p.getScoreboard() == board) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Met à jour les lignes seulement si elles ont changé. */
    public void setLines(List<Component> lines) {
        if (lines.equals(current)) return;
        for (int i = 0; i < lines.size(); i++) {
            Score s = objective.getScore("line" + i);
            s.setScore(lines.size() - i);
            s.customName(lines.get(i));
        }
        for (int i = lines.size(); i < current.size(); i++) board.resetScores("line" + i);
        current = new ArrayList<>(lines);
    }
}
