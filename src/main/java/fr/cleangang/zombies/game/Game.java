package fr.cleangang.zombies.game;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.config.PluginSettings;
import fr.cleangang.zombies.config.WeaponDefinition;
import fr.cleangang.zombies.map.Barricade;
import fr.cleangang.zombies.map.BuiltArena;
import fr.cleangang.zombies.map.DoorRuntime;
import fr.cleangang.zombies.map.MapDefinition;
import fr.cleangang.zombies.ui.Hud;
import fr.cleangang.zombies.util.Msg;
import fr.cleangang.zombies.zombie.ZombieManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Une partie de Zombies : salon d'attente → manches → écran de fin.
 * Une seule boucle (1 tick) pilote les sous-systèmes : manches, zombies, barricades, interface.
 */
public final class Game {

    public enum State {LOBBY, RUNNING, ENDING}

    private static final int END_SCREEN_TICKS = 200;

    private final ZombiesPlugin plugin;
    private final GameManager manager;
    private final PluginSettings s;
    private final MapDefinition map;
    private final BuiltArena arena;
    private final Random rng;

    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private final Set<UUID> pendingPack = new HashSet<>();
    private final EconomyManager economy = new EconomyManager();
    private final ZombieManager zombies;
    private final RoundManager rounds;
    private final BarricadeManager barricades;
    private final Hud hud = new Hud();

    private State state = State.LOBBY;
    private long tick;
    private int lobbyTicks = -1;
    private int endTicks;
    private long startedAt;
    private boolean applyingDamage;
    private boolean victory;
    private int nextPlayerIndex;
    private BukkitTask loop;

    Game(ZombiesPlugin plugin, GameManager manager, BuiltArena arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.s = plugin.settings();
        this.map = arena.def;
        this.arena = arena;
        this.rng = new Random(arena.seed);
        this.zombies = new ZombieManager(plugin, this, rng);
        this.rounds = new RoundManager(this, s.rounds);
        this.barricades = new BarricadeManager(this, s);
    }

    void open() {
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLoop, 1L, 1L);
    }

    // ── Accès ────────────────────────────────────────────────────

    public State state() {
        return state;
    }

    public MapDefinition map() {
        return map;
    }

    public BuiltArena arena() {
        return arena;
    }

    public ZombieManager zombies() {
        return zombies;
    }

    public RoundManager rounds() {
        return rounds;
    }

    public EconomyManager economy() {
        return economy;
    }

    public PluginSettings settings() {
        return s;
    }

    public long tick() {
        return tick;
    }

    public boolean isApplyingDamage() {
        return applyingDamage;
    }

    public boolean has(Player p) {
        return players.containsKey(p.getUniqueId());
    }

    public boolean isPending(Player p) {
        return pendingPack.contains(p.getUniqueId());
    }

    public GamePlayer player(Player p) {
        return players.get(p.getUniqueId());
    }

    public int playerCount() {
        return players.size();
    }

    public List<GamePlayer> players() {
        return new ArrayList<>(players.values());
    }

    /** Joueurs en ligne, vivants, ciblables par les zombies. */
    public List<Player> aliveOnline() {
        List<Player> out = new ArrayList<>();
        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            if (p != null && gp.alive) out.add(p);
        }
        return out;
    }

    // ── Entrée / sortie des joueurs ─────────────────────────────

    /** Demande le pack de ressources puis fait entrer le joueur. */
    void addPlayer(Player p) {
        if (state != State.LOBBY) {
            Msg.send(p, "<red>Une partie est déjà en cours sur <white><map></white>. Réessaie à la fin.",
                    Placeholder.unparsed("map", map.displayName));
            return;
        }
        if (players.size() + pendingPack.size() >= s.maxPlayers) {
            Msg.send(p, "<red>La partie est complète (<max> joueurs).", Placeholder.unparsed("max", String.valueOf(s.maxPlayers)));
            return;
        }
        pendingPack.add(p.getUniqueId());
        Msg.send(p, "<gray>Chargement du pack Zombies…");
        plugin.packs().request(p,
                () -> admit(p),
                reason -> {
                    pendingPack.remove(p.getUniqueId());
                    Msg.send(p, "<red>Le pack de ressources est obligatoire pour jouer au mode Zombies <dark_gray>(<reason>)</dark_gray>.",
                            Placeholder.unparsed("reason", reason));
                    Msg.send(p, "<gray>Active-le dans <white>Multijoueur → Modifier → Packs de ressources : Activés</white>, puis réessaie.");
                    plugin.games().sendToHub(p);
                    if (players.isEmpty() && pendingPack.isEmpty()) manager.endGame(this, false);
                });
    }

    private void admit(Player p) {
        pendingPack.remove(p.getUniqueId());
        if (!p.isOnline()) return;
        if (state != State.LOBBY || players.size() >= s.maxPlayers) {
            Msg.send(p, "<red>La partie a démarré ou est complète entre-temps.");
            return;
        }
        if (!plugin.snapshots().save(p)) {
            Msg.send(p, "<red>Impossible de sauvegarder ton inventaire : entrée annulée par sécurité.");
            return;
        }
        GamePlayer gp = new GamePlayer(p, nextPlayerIndex++);
        players.put(p.getUniqueId(), gp);
        preparePlayer(p, gp);
        p.teleport(arena.spawnFor(gp.index));
        hud.attach(p);
        broadcast("<gray><player> a rejoint la partie <dark_gray>(<count>/<max>)",
                Placeholder.unparsed("player", p.getName()),
                Placeholder.unparsed("count", String.valueOf(players.size())),
                Placeholder.unparsed("max", String.valueOf(s.maxPlayers)));

        if (players.size() >= s.minPlayers && lobbyTicks < 0) lobbyTicks = s.lobbyCountdownSeconds * 20;
        if (players.size() >= s.maxPlayers) lobbyTicks = Math.min(lobbyTicks, s.fullLobbyCountdownSeconds * 20);
    }

    private void preparePlayer(Player p, GamePlayer gp) {
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setGameMode(GameMode.ADVENTURE);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) max.setBaseValue(s.playerMaxHealth);
        p.setHealth(s.playerMaxHealth);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0);
        gp.alive = true;
    }

    /**
     * Retire un joueur et lui rend ses affaires.
     *
     * @param teleportBack le renvoyer à sa position d'avant la partie (false s'il a déjà changé de monde)
     */
    public void removePlayer(Player p, boolean teleportBack) {
        boolean wasPending = pendingPack.remove(p.getUniqueId());
        GamePlayer gp = players.remove(p.getUniqueId());
        if (gp == null) {
            // Il attendait encore son pack : si plus personne n'est là, on libère l'arène.
            if (wasPending && state != State.ENDING && players.isEmpty() && pendingPack.isEmpty()) manager.endGame(this, false);
            return;
        }
        gp.cancelReload();
        hud.detach(p);
        plugin.packs().unload(p);
        if (!plugin.snapshots().restore(p, teleportBack) && teleportBack) plugin.games().sendToHub(p);

        if (state == State.ENDING) return;
        if (players.isEmpty() && pendingPack.isEmpty()) {
            manager.endGame(this, false);
        } else if (players.isEmpty()) {
            lobbyTicks = -1; // salon vide en attendant les packs : on suspend le compte à rebours
        } else {
            broadcast("<gray><player> a quitté la partie.", Placeholder.unparsed("player", p.getName()));
            if (state == State.RUNNING && aliveOnline().isEmpty()) gameOver();
        }
    }

    // ── Boucle ───────────────────────────────────────────────────

    private void tickLoop() {
        tick++;
        switch (state) {
            case LOBBY -> tickLobby();
            case RUNNING -> tickRunning();
            case ENDING -> {
                if (--endTicks <= 0) {
                    manager.endGame(this, true);
                    return;
                }
            }
        }
        if (tick % 5 == 0) updateHud();
    }

    private void tickLobby() {
        if (players.isEmpty() || lobbyTicks < 0) return;
        lobbyTicks--;
        if (lobbyTicks % 20 == 0) {
            int sec = lobbyTicks / 20;
            if (sec > 0 && (sec <= 5 || sec % 5 == 0)) {
                for (Player p : online()) s.sound("countdown").playTo(p);
            }
        }
        if (lobbyTicks <= 0) beginMatch();
    }

    private void tickRunning() {
        rounds.tick();
        if (state != State.RUNNING) return; // victoire possible pendant rounds.tick()
        if (tick % ZombieManager.TICK_PERIOD == 0) {
            int requeue = zombies.tick(aliveOnline());
            if (requeue > 0) rounds.requeue(requeue);
        }
        barricades.tick();
        if (tick % 20 == 0) keepInBounds();
    }

    /** Démarre immédiatement (commande admin ou fin du compte à rebours). */
    public void beginMatch() {
        if (state != State.LOBBY || players.isEmpty()) return;
        state = State.RUNNING;
        startedAt = System.currentTimeMillis();
        for (GamePlayer gp : players.values()) {
            economy.reset(gp, s.points.start);
            Player p = gp.player();
            if (p != null) giveLoadout(p);
        }
        rounds.startRound(1);
    }

    private void giveLoadout(Player p) {
        p.getInventory().clear();
        WeaponDefinition start = plugin.weapons().get(s.startingWeapon);
        if (start != null) p.getInventory().setItem(0, plugin.weaponService().createItem(start));
        p.getInventory().setHeldItemSlot(0);
    }

    private void keepInBounds() {
        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            if (p == null) continue;
            if (!arena.contains(p.getLocation(), gp.alive ? 2 : 8)) p.teleport(arena.spawnFor(gp.index));
        }
    }

    // ── Événements de manche ─────────────────────────────────────

    void onRoundStart(int r) {
        for (Barricade b : arena.barricades) b.resetRoundCaps();
        arena.world.setTime(fr.cleangang.zombies.map.ArenaWorld.NIGHT);
        if (r > 1 && s.respawnNextRound) respawnDead();
        Title title = Title.title(
                Msg.mm("<dark_red><bold>Manche " + r),
                Msg.mm("<gray>" + rounds.remaining() + " zombies"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(700)));
        for (Player p : online()) {
            p.showTitle(title);
            s.sound("round-start").playTo(p);
        }
    }

    void onRoundEnd(int r) {
        for (Player p : online()) {
            p.showTitle(Title.title(Msg.mm("<gray>Manche " + r + " terminée"),
                    Msg.mm("<dark_gray>Prochaine manche dans " + s.rounds.intermissionSeconds + " s"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(500))));
            s.sound("round-end").playTo(p);
        }
    }

    private void respawnDead() {
        for (GamePlayer gp : players.values()) {
            if (gp.alive) continue;
            Player p = gp.player();
            if (p == null) continue;
            preparePlayer(p, gp);
            giveLoadout(p);
            p.teleport(arena.spawnFor(gp.index));
            Msg.send(p, "<green>Tu es de retour pour la manche " + rounds.round() + " !");
        }
    }

    void onVictory() {
        victory = true;
        gameOver();
    }

    // ── Combat ───────────────────────────────────────────────────

    /** Applique des dégâts à un zombie de la partie et distribue les points de touche. */
    public void damageZombie(Player shooter, Zombie z, double amount, boolean headshot, boolean melee) {
        GamePlayer gp = player(shooter);
        ZombieManager.ZombieData d = zombies.data(z);
        if (gp == null || d == null || !gp.alive || state != State.RUNNING || z.isDead()) return;
        boolean lethal = z.getHealth() - amount <= 0;
        d.lastHitBy = shooter.getUniqueId();
        d.lastHitHeadshot = headshot;
        d.lastHitMelee = melee;
        d.lastHitTick = tick;
        applyingDamage = true;
        try {
            z.damage(amount, shooter);
        } finally {
            applyingDamage = false;
        }
        if (!lethal) economy.reward(gp, s.points.hit);
    }

    /** Appelé par le listener quand un de nos zombies meurt. */
    public void onZombieDeath(Zombie z) {
        ZombieManager.ZombieData d = zombies.onDeath(z);
        if (d == null || d.lastHitBy == null) return;
        GamePlayer gp = players.get(d.lastHitBy);
        if (gp == null) return;
        int pts = s.points.kill;
        if (d.lastHitHeadshot) {
            pts += s.points.headshotBonus;
            gp.headshots++;
        }
        if (d.lastHitMelee) pts += s.points.meleeKillBonus;
        economy.reward(gp, pts);
        gp.kills++;
    }

    /** Coup fatal reçu : le joueur tombe (la réanimation arrive à l'étape 3). */
    public void onPlayerDown(Player p) {
        GamePlayer gp = player(p);
        if (gp == null || !gp.alive || state != State.RUNNING) return;
        gp.alive = false;
        gp.downs++;
        gp.cancelReload();
        Location at = p.getLocation();
        s.sound("down").play(at, SoundCategory.PLAYERS);
        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        p.setHealth(Math.max(1.0, p.getHealth()));
        p.showTitle(Title.title(Msg.mm("<dark_red><bold>Tu es tombé"),
                Msg.mm(s.respawnNextRound && players.size() > 1 ? "<gray>Retour à la prochaine manche" : "<gray>Observation des survivants"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(600))));
        broadcast("<red><player> est tombé !", Placeholder.unparsed("player", p.getName()));
        if (aliveOnline().isEmpty()) gameOver();
    }

    // ── Achats ───────────────────────────────────────────────────

    public void tryOpenDoor(Player p, DoorRuntime door) {
        GamePlayer gp = player(p);
        if (gp == null || !gp.alive || door.isOpen()) return;
        if (state != State.RUNNING) {
            Msg.send(p, "<gray>Les portes s'achètent une fois la partie lancée.");
            return;
        }
        if (!economy.spend(gp, door.def.cost())) {
            s.sound("deny").playTo(p);
            p.sendActionBar(Msg.mm("<red>Il te faut <bold><cost></bold> points", Placeholder.unparsed("cost", Msg.formatPoints(door.def.cost()))));
            return;
        }
        arena.openDoor(door);
        s.sound("purchase").playTo(p);
        s.sound("door-open").play(door.center(), SoundCategory.BLOCKS);
        broadcast("<gold><player> <gray>a ouvert l'accès : <white><door>",
                Placeholder.unparsed("player", p.getName()), Placeholder.unparsed("door", door.def.name()));
    }

    // ── Fin de partie ────────────────────────────────────────────

    void gameOver() {
        if (state == State.ENDING) return;
        state = State.ENDING;
        endTicks = END_SCREEN_TICKS;
        zombies.clearAll();
        long duration = System.currentTimeMillis() - startedAt;
        int reached = rounds.round();

        Title title = victory
                ? Title.title(Msg.mm("<gold><bold>Victoire"), Msg.mm("<gray>Vous avez survécu à " + reached + " manches"))
                : Title.title(Msg.mm("<dark_red><bold>Partie terminée"), Msg.mm("<gray>Vous avez survécu " + plural(reached, "manche")));
        List<Component> summary = new ArrayList<>();
        summary.add(Msg.mm("<dark_red><strikethrough>                    </strikethrough> <bold>FIN DE PARTIE</bold> <strikethrough>                    "));
        summary.add(Msg.mm("<gray>Carte <white><map></white> · Manche <white><round></white> · Durée <white><time>",
                Placeholder.unparsed("map", map.displayName),
                Placeholder.unparsed("round", String.valueOf(reached)),
                Placeholder.unparsed("time", Msg.formatDuration(duration))));
        for (GamePlayer gp : players.values()) {
            summary.add(Msg.mm(" <white><name></white> <dark_gray>—</dark_gray> <gold><pts> pts</gold> <dark_gray>·</dark_gray> <gray><kills> élim. (<hs> têtes) · <downs> chute(s)",
                    Placeholder.unparsed("name", gp.name),
                    Placeholder.unparsed("pts", Msg.formatPoints(gp.pointsEarned)),
                    Placeholder.unparsed("kills", String.valueOf(gp.kills)),
                    Placeholder.unparsed("hs", String.valueOf(gp.headshots)),
                    Placeholder.unparsed("downs", String.valueOf(gp.downs))));
        }
        Component replay = Msg.mm("<green><bold>[Rejouer]</bold></green>")
                .clickEvent(ClickEvent.runCommand("/zombies join " + map.id))
                .append(Msg.mm(" <dark_gray>|</dark_gray> "))
                .append(Msg.mm("<yellow>[Choisir une carte]").clickEvent(ClickEvent.runCommand("/zombies menu")));

        for (Player p : online()) {
            p.showTitle(title);
            s.sound("game-over").playTo(p);
            for (Component line : summary) p.sendMessage(line);
            p.sendMessage(replay);
            GamePlayer gp = player(p);
            if (gp != null && gp.alive) p.setGameMode(GameMode.SPECTATOR);
        }
    }

    /** Arrêt immédiat : nettoie tout et rend leurs affaires aux joueurs. */
    void close() {
        if (loop != null) loop.cancel();
        zombies.clearAll();
        state = State.ENDING;
        for (GamePlayer gp : new ArrayList<>(players.values())) {
            Player p = gp.player();
            if (p != null) removePlayer(p, true);
        }
        players.clear();
        pendingPack.clear();
    }

    // ── Interface ────────────────────────────────────────────────

    private void updateHud() {
        List<Component> lines = new ArrayList<>();
        lines.add(Msg.mm("<dark_gray>" + map.displayName));
        lines.add(Component.empty());
        switch (state) {
            case LOBBY -> {
                lines.add(Msg.mm("<gray>Joueurs : <white>" + players.size() + "/" + s.maxPlayers));
                lines.add(Msg.mm(lobbyTicks >= 0 ? "<gray>Début dans <yellow>" + (lobbyTicks / 20 + 1) + " s" : "<gray>En attente…"));
            }
            case RUNNING, ENDING -> {
                lines.add(Msg.mm("<gray>Manche <dark_red><bold>" + Math.max(1, rounds.round())));
                lines.add(Msg.mm(rounds.inIntermission() ? "<green>Pause" : "<gray>Zombies <white>" + rounds.remaining()));
            }
        }
        lines.add(Component.space());
        for (GamePlayer gp : players.values()) {
            String name = gp.alive ? "<white>" + gp.name + "</white>" : "<dark_gray><strikethrough>" + gp.name + "</strikethrough></dark_gray>";
            lines.add(Msg.mm(name + " <gold>" + Msg.formatPoints(gp.points)));
        }
        hud.setLines(lines);

        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            if (p == null) continue;
            if (state == State.LOBBY) {
                if (lobbyTicks >= 0) p.sendActionBar(Msg.mm("<gray>Début dans <yellow><bold>" + (lobbyTicks / 20 + 1) + "</bold> s"));
                continue;
            }
            if (state != State.RUNNING || !gp.alive) continue;
            Component hint = interactionHint(p);
            p.sendActionBar(hint != null ? hint : plugin.weaponService().ammoLine(gp, p));
        }
    }

    private Component interactionHint(Player p) {
        Block target = p.getTargetBlockExact(4);
        DoorRuntime door = arena.doorAt(target);
        if (door != null && !door.isOpen()) {
            return Msg.mm("<gray>Clic droit : dégager les débris <gold>[<cost>]", Placeholder.unparsed("cost", Msg.formatPoints(door.def.cost())));
        }
        Barricade near = barricades.repairableNear(p);
        if (near != null) return Msg.mm("<gray>Accroupis-toi pour réparer la fenêtre <white>(" + near.planks() + "/" + near.size() + ")");
        return null;
    }

    // ── Utilitaires ──────────────────────────────────────────────

    public List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            if (p != null) out.add(p);
        }
        return out;
    }

    public void broadcast(String mini, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        Component c = Msg.prefixed(mini, resolvers);
        for (Player p : online()) p.sendMessage(c);
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n > 1 ? "s" : "");
    }
}
