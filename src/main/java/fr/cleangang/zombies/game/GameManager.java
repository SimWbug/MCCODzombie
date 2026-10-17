package fr.cleangang.zombies.game;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.config.PluginSettings;
import fr.cleangang.zombies.map.ArenaBuilder;
import fr.cleangang.zombies.map.ArenaValidator;
import fr.cleangang.zombies.map.ArenaWorld;
import fr.cleangang.zombies.map.Barricade;
import fr.cleangang.zombies.map.BuiltArena;
import fr.cleangang.zombies.map.DoorRuntime;
import fr.cleangang.zombies.map.MapDefinition;
import fr.cleangang.zombies.util.Msg;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Point d'entrée des parties. Une arène = une partie à la fois (les 4 cartes partagent le même emplacement,
 * reconstruit à chaque lancement). Gère aussi le mode diagnostic.
 */
public final class GameManager {

    private static final Particle.DustOptions DOOR_DUST = new Particle.DustOptions(Color.ORANGE, 1.0f);

    private final ZombiesPlugin plugin;
    private Game current;
    private BuiltArena lastArena;
    private String selectedMap;
    private final Set<UUID> debug = new HashSet<>();
    private BukkitTask debugTask;

    public GameManager(ZombiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        selectedMap = plugin.settings().defaultMap;
        debugTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderDebug, 20L, 10L);
    }

    public Game current() {
        return current;
    }

    public String selectedMap() {
        return selectedMap;
    }

    public boolean selectMap(String id) {
        MapDefinition m = plugin.maps().get(id);
        if (m == null) return false;
        selectedMap = m.id;
        return true;
    }

    /** Partie dans laquelle se trouve (ou entre) ce joueur. */
    public Game gameOf(Player p) {
        return current != null && (current.has(p) || current.isPending(p)) ? current : null;
    }

    public World arenaWorld() {
        return ArenaWorld.getOrCreate(plugin.settings().arenaWorld);
    }

    // ── Rejoindre / quitter ─────────────────────────────────────

    public void join(Player p, String mapId) {
        if (gameOf(p) != null) {
            Msg.send(p, "<gray>Tu es déjà dans la partie. <white>/zombies leave</white> pour la quitter.");
            return;
        }
        MapDefinition map = plugin.maps().get(mapId == null ? selectedMap : mapId);
        if (map == null) {
            Msg.send(p, "<red>Carte inconnue.");
            return;
        }
        if (!map.playable) {
            Msg.send(p, "<gold><map></gold> <gray>arrive bientôt ! Pour l'instant, seul <white>Bunker 13</white> est jouable.",
                    Placeholder.unparsed("map", map.displayName));
            return;
        }
        if (current != null) {
            if (!current.map().id.equals(map.id)) {
                Msg.send(p, "<red>Une partie est déjà ouverte sur <white><map></white>.", Placeholder.unparsed("map", current.map().displayName));
                return;
            }
            current.addPlayer(p);
            return;
        }
        Game g = createGame(map, p);
        if (g != null) g.addPlayer(p);
    }

    public void leave(Player p, boolean teleportBack) {
        Game g = gameOf(p);
        if (g != null) g.removePlayer(p, teleportBack);
    }

    /** Construit l'arène, la vérifie et ouvre une partie. Renvoie null (avec message) si la carte est cassée. */
    public Game createGame(MapDefinition map, CommandSender feedback) {
        BuiltArena arena = buildArena(map, System.nanoTime(), feedback);
        if (arena == null) return null;
        current = new Game(plugin, this, arena);
        current.open();
        return current;
    }

    /** Construit et valide une carte. Renvoie null et affiche les erreurs si la validation échoue. */
    public BuiltArena buildArena(MapDefinition map, long seed, CommandSender feedback) {
        World w = arenaWorld();
        if (w == null) {
            Msg.send(feedback, "<red>Impossible de créer le monde de l'arène.");
            return null;
        }
        PluginSettings s = plugin.settings();
        long t0 = System.nanoTime();
        BuiltArena arena = new ArenaBuilder(w, s.originX, s.originY, s.originZ).build(map, seed);
        List<String> errors = ArenaValidator.validate(arena);
        lastArena = arena;
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (!errors.isEmpty()) {
            Msg.send(feedback, "<red>La carte <white><map></white> n'a pas passé la vérification, partie annulée :",
                    Placeholder.unparsed("map", map.displayName));
            errors.stream().limit(8).forEach(e -> feedback.sendMessage(Msg.mm("<red> • <gray><e>", Placeholder.unparsed("e", e))));
            plugin.getLogger().warning("Validation de " + map.id + " échouée : " + errors);
            return null;
        }
        plugin.getLogger().info("Carte " + map.id + " construite et vérifiée en " + ms + " ms (graine " + seed + ")");
        return arena;
    }

    /** Fin de partie. {@code normal} = après l'écran de fin ; sinon arrêt immédiat. */
    void endGame(Game g, boolean normal) {
        if (g != current) return;
        current = null;
        g.close();
    }

    public void stop() {
        if (current != null) endGame(current, false);
    }

    public void shutdown() {
        stop();
        if (debugTask != null) debugTask.cancel();
    }

    /** Envoie le joueur au hub (commande configurée, sinon spawn du monde principal). */
    public void sendToHub(Player p) {
        String cmd = plugin.settings().hubCommand;
        if (!cmd.isEmpty()) {
            p.performCommand(cmd);
        } else {
            p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
    }

    // ── Diagnostic ───────────────────────────────────────────────

    public boolean toggleDebug(Player p) {
        if (!debug.remove(p.getUniqueId())) {
            debug.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    public BuiltArena displayedArena() {
        return current != null ? current.arena() : lastArena;
    }

    private void renderDebug() {
        if (debug.isEmpty()) return;
        BuiltArena arena = displayedArena();
        if (arena == null) return;
        for (UUID id : debug) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || p.getWorld() != arena.world) continue;
            for (Barricade b : arena.barricades) {
                boolean active = arena.isUnlocked(b.roomId);
                p.spawnParticle(active ? Particle.FLAME : Particle.SMOKE, b.spawn.clone().add(0, 1, 0), 4, 0.2, 0.4, 0.2, 0);
                p.spawnParticle(Particle.HAPPY_VILLAGER, b.insideAnchor.clone().add(0, 0.2, 0), 3, 0.2, 0.1, 0.2, 0);
                p.spawnParticle(Particle.CRIT, b.outsideAnchor.clone().add(0, 0.2, 0), 3, 0.2, 0.1, 0.2, 0);
            }
            for (DoorRuntime d : arena.doors) {
                if (d.isOpen()) continue;
                for (Block c : d.cells()) {
                    p.spawnParticle(Particle.DUST, c.getLocation().add(0.5, 0.5, 0.5), 1, 0.2, 0.2, 0.2, 0, DOOR_DUST);
                }
            }
            for (Location s : arena.playerSpawns) p.spawnParticle(Particle.HEART, s.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
            Location c1 = new Location(arena.world, arena.bounds.getMinX(), arena.bounds.getMinY() + 1, arena.bounds.getMinZ());
            Location c2 = new Location(arena.world, arena.bounds.getMaxX(), arena.bounds.getMinY() + 1, arena.bounds.getMaxZ());
            p.spawnParticle(Particle.END_ROD, c1, 1, 0, 0, 0, 0);
            p.spawnParticle(Particle.END_ROD, c2, 1, 0, 0, 0, 0);
        }
    }

    /** Rapport texte de l'arène affichée (commande /zombies debug). */
    public void report(CommandSender to) {
        BuiltArena a = displayedArena();
        if (a == null) {
            Msg.send(to, "<gray>Aucune arène construite. Lance <white>/zombies reset</white> pour en construire une.");
            return;
        }
        Msg.send(to, "<gray>Arène <white><map></white> · graine <white><seed></white>",
                Placeholder.unparsed("map", a.def.displayName), Placeholder.unparsed("seed", String.valueOf(a.seed)));
        to.sendMessage(Msg.mm("<gray> Salles ouvertes : <white>" + String.join(", ", a.unlockedRooms())));
        to.sendMessage(Msg.mm("<gray> Fenêtres actives : <white>" + a.activeBarricades().size() + "/" + a.barricades.size()));
        for (DoorRuntime d : a.doors) {
            to.sendMessage(Msg.mm("<gray> Porte <white>" + d.def.id() + "</white> (" + d.def.cost() + " pts) : " + (d.isOpen() ? "<green>ouverte" : "<red>fermée")));
        }
        List<String> errors = ArenaValidator.validate(a);
        to.sendMessage(errors.isEmpty() ? Msg.mm("<green> ✔ Chemins zombies → joueurs valides") : Msg.mm("<red> ✘ " + errors.size() + " problème(s) :"));
        errors.stream().limit(8).forEach(e -> to.sendMessage(Msg.mm("<red>   • <gray><e>", Placeholder.unparsed("e", e))));
        to.sendMessage(Msg.mm("<dark_gray> Particules : flamme = apparition active, fumée = inactive, vert = réparation, orange = porte, cœur = départ joueur"));
    }
}
