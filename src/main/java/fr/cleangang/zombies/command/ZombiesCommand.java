package fr.cleangang.zombies.command;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.game.Game;
import fr.cleangang.zombies.game.GameManager;
import fr.cleangang.zombies.map.MapDefinition;
import fr.cleangang.zombies.ui.MapMenu;
import fr.cleangang.zombies.util.Msg;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /zombies menu | join [carte] | leave                       (joueurs)
 * /zombies start [carte] | stop | reset [carte] | map &lt;carte&gt; | status | debug | reload   (admins)
 */
public final class ZombiesCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("menu", "join", "leave");
    private static final List<String> ADMIN_SUBS = List.of("start", "stop", "reset", "map", "status", "debug", "reload");
    private static final String ADMIN = "cleanzombies.admin";

    private final ZombiesPlugin plugin;

    public ZombiesCommand(ZombiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        String arg = args.length > 1 ? args[1] : null;
        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission(ADMIN)) {
            Msg.send(sender, "<red>Tu n'as pas la permission.");
            return true;
        }
        if (!plugin.isReady() && !sub.equals("reload")) {
            Msg.send(sender, "<red>Configuration invalide : corrige les erreurs listées en console puis fais <white>/zombies reload</white>.");
            return true;
        }
        GameManager games = plugin.games();
        switch (sub) {
            case "menu" -> {
                if (sender instanceof Player p) new MapMenu(plugin).open(p);
                else Msg.send(sender, "<red>Commande réservée aux joueurs.");
            }
            case "join" -> {
                if (sender instanceof Player p) games.join(p, arg);
                else Msg.send(sender, "<red>Commande réservée aux joueurs.");
            }
            case "leave" -> {
                if (sender instanceof Player p && games.gameOf(p) != null) {
                    games.leave(p, true);
                    Msg.send(p, "<gray>Tu as quitté la partie.");
                } else {
                    Msg.send(sender, "<gray>Tu n'es dans aucune partie.");
                }
            }
            case "start" -> start(sender, arg);
            case "stop" -> {
                if (games.current() == null) {
                    Msg.send(sender, "<gray>Aucune partie en cours.");
                } else {
                    games.stop();
                    Msg.send(sender, "<green>Partie arrêtée, joueurs restaurés.");
                }
            }
            case "reset" -> {
                if (games.current() != null) {
                    Msg.send(sender, "<red>Une partie est en cours : <white>/zombies stop</white> d'abord.");
                    return true;
                }
                MapDefinition map = plugin.maps().get(arg == null ? games.selectedMap() : arg);
                if (map == null || !map.playable) {
                    Msg.send(sender, "<red>Carte inconnue ou pas encore jouable.");
                    return true;
                }
                if (games.buildArena(map, System.nanoTime(), sender) != null) {
                    Msg.send(sender, "<green>Arène <white><map></white> reconstruite et vérifiée.", Placeholder.unparsed("map", map.displayName));
                }
            }
            case "map" -> {
                if (arg == null || !games.selectMap(arg)) {
                    Msg.send(sender, "<red>Usage : /zombies map [" + String.join(" | ", plugin.maps().ids()) + "]");
                } else {
                    Msg.send(sender, "<green>Carte par défaut : <white><map>", Placeholder.unparsed("map", plugin.maps().get(arg).displayName));
                }
            }
            case "status" -> status(sender);
            case "debug" -> {
                if (sender instanceof Player p) {
                    boolean on = games.toggleDebug(p);
                    Msg.send(p, on ? "<green>Diagnostic activé." : "<gray>Diagnostic désactivé.");
                    if (on) games.report(p);
                } else {
                    games.report(sender);
                }
            }
            case "reload" -> {
                if (games.current() != null) {
                    Msg.send(sender, "<red>Rechargement refusé pendant une partie (risque de casser la partie en cours). <white>/zombies stop</white> d'abord.");
                    return true;
                }
                List<String> errors = plugin.reloadData();
                if (errors.isEmpty()) {
                    Msg.send(sender, "<green>Configuration rechargée : <white>" + plugin.maps().ids().size() + "</white> cartes, <white>"
                            + plugin.weapons().size() + "</white> armes.");
                } else {
                    Msg.send(sender, "<red>" + errors.size() + " erreur(s), ancienne configuration conservée :");
                    errors.stream().limit(10).forEach(e -> sender.sendMessage(Msg.mm("<red> • <gray><e>", Placeholder.unparsed("e", e))));
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private void start(CommandSender sender, String mapArg) {
        GameManager games = plugin.games();
        Game g = games.current();
        if (g == null) {
            if (!(sender instanceof Player p)) {
                Msg.send(sender, "<red>Depuis la console, ouvre d'abord une partie en jeu.");
                return;
            }
            games.join(p, mapArg);
            g = games.current();
            if (g == null) return;
            Msg.send(sender, "<gray>Partie ouverte. Elle démarrera dès que ton pack sera chargé ; refais <white>/zombies start</white> pour forcer.");
            return;
        }
        if (g.state() != Game.State.LOBBY) {
            Msg.send(sender, "<gray>La partie est déjà lancée.");
            return;
        }
        if (g.playerCount() == 0) {
            Msg.send(sender, "<red>Personne n'est encore entré dans la partie.");
            return;
        }
        g.beginMatch();
        Msg.send(sender, "<green>Partie lancée.");
    }

    private void status(CommandSender sender) {
        Game g = plugin.games().current();
        if (g == null) {
            Msg.send(sender, "<gray>Aucune partie. Carte par défaut : <white>" + plugin.games().selectedMap());
            return;
        }
        Msg.send(sender, "<gray>Carte <white><map></white> · état <white><state></white> · manche <white><round></white>",
                Placeholder.unparsed("map", g.map().displayName),
                Placeholder.unparsed("state", g.state().name()),
                Placeholder.unparsed("round", String.valueOf(g.rounds().round())));
        sender.sendMessage(Msg.mm("<gray> Joueurs : <white>" + g.playerCount() + "</white> · zombies en vie : <white>"
                + g.zombies().alive() + "</white> · restants : <white>" + g.rounds().remaining()));
    }

    private void help(CommandSender sender) {
        Msg.send(sender, "<gray>Commandes :");
        sender.sendMessage(Msg.mm("<white> /zombies menu</white> <dark_gray>— choisir une carte"));
        sender.sendMessage(Msg.mm("<white> /zombies join [carte]</white> <dark_gray>— rejoindre"));
        sender.sendMessage(Msg.mm("<white> /zombies leave</white> <dark_gray>— quitter"));
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage(Msg.mm("<white> /zombies start [carte] · stop · reset [carte] · map [carte] · status · debug · reload"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER_SUBS);
            if (sender.hasPermission(ADMIN)) options.addAll(ADMIN_SUBS);
        } else if (args.length == 2 && List.of("join", "start", "reset", "map").contains(args[0].toLowerCase(Locale.ROOT))) {
            options.addAll(plugin.maps().ids());
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.startsWith(prefix));
        return options;
    }
}
