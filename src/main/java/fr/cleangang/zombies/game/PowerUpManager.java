package fr.cleangang.zombies.game;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.config.PluginSettings;
import fr.cleangang.zombies.util.Msg;
import fr.cleangang.zombies.zombie.ZombieManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Bonus lâchés par les zombies : Munitions max, Bombe, Points x2.
 * Un bonus flotte en tournant face aux joueurs, clignote avant de disparaître,
 * et se ramasse en passant dessus. Il profite à toute l'équipe.
 */
public final class PowerUpManager {

    public enum Type {
        MAX_AMMO("powerup_max_ammo", "<green><bold>MUNITIONS MAX", Color.fromRGB(80, 220, 90)),
        NUKE("powerup_nuke", "<gold><bold>BOMBE", Color.fromRGB(255, 170, 30)),
        DOUBLE_POINTS("powerup_double_points", "<yellow><bold>POINTS x2", Color.fromRGB(255, 220, 40));

        final String model;
        final String title;
        final Color glow;

        Type(String model, String title, Color glow) {
            this.model = model;
            this.title = title;
            this.glow = glow;
        }

        String configKey() {
            return name().toLowerCase().replace('_', '-');
        }
    }

    private record Drop(Type type, ItemDisplay display, Location base, long spawnTick) {
    }

    private static final int PERIOD = 2;

    private final ZombiesPlugin plugin;
    private final Game game;
    private final Random rng;
    private final List<Drop> drops = new ArrayList<>();
    private int dropsThisRound;
    private long doubleUntil = -1;

    PowerUpManager(ZombiesPlugin plugin, Game game, Random rng) {
        this.plugin = plugin;
        this.game = game;
        this.rng = rng;
    }

    private PluginSettings.PowerUps cfg() {
        return game.settings().powerUps;
    }

    void onRoundStart() {
        dropsThisRound = 0;
    }

    /** Secondes restantes de Points x2 (0 si inactif). */
    public int doublePointsSeconds() {
        return doubleUntil < 0 ? 0 : (int) Math.max(0, (doubleUntil - game.tick()) / 20 + 1);
    }

    /** Un zombie vient d'être tué par un joueur : tirage d'un bonus. */
    void onZombieKilled(Location at) {
        PluginSettings.PowerUps c = cfg();
        if (!c.enabled || dropsThisRound >= c.maxPerRound || rng.nextDouble() >= c.dropChance) return;
        if (!game.arena().contains(at, 0)) return;
        List<Type> pool = new ArrayList<>();
        int total = 0;
        for (Type t : Type.values()) {
            int w = c.weights.getOrDefault(t.configKey(), 0);
            if (w > 0) {
                pool.add(t);
                total += w;
            }
        }
        if (total == 0) return;
        int r = rng.nextInt(total);
        Type chosen = pool.getLast();
        for (Type t : pool) {
            r -= c.weights.get(t.configKey());
            if (r < 0) {
                chosen = t;
                break;
            }
        }
        spawn(chosen, at);
    }

    private void spawn(Type type, Location at) {
        dropsThisRound++;
        Location base = at.clone().add(0, 1.0, 0);
        base.setPitch(0);
        ItemStack icon = new ItemStack(Material.PAPER);
        ItemMeta meta = icon.getItemMeta();
        meta.setItemModel(new NamespacedKey("cgzombies", type.model));
        icon.setItemMeta(meta);
        ItemDisplay d = Displays.item(plugin.keys(), base, icon, Display.Billboard.VERTICAL);
        d.setGlowing(true);
        d.setGlowColorOverride(type.glow);
        drops.add(new Drop(type, d, base, game.tick()));
        game.settings().sound("powerup-spawn").play(base, SoundCategory.PLAYERS);
    }

    void tick() {
        long now = game.tick();
        if (doubleUntil >= 0 && now >= doubleUntil) {
            doubleUntil = -1;
            game.economy().setMultiplier(1);
            game.broadcast("<gray>Fin des points x2.");
        }
        if (now % PERIOD != 0 || drops.isEmpty()) return;
        int lifetime = cfg().lifetimeSeconds * 20;
        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop drop = it.next();
            long age = now - drop.spawnTick();
            if (!drop.display().isValid() || age > lifetime) {
                Displays.remove(drop.display());
                it.remove();
                continue;
            }
            // Flotte de haut en bas, clignote pendant les 6 dernières secondes
            if (age % 6 == 0) {
                double bob = Math.sin(age / 12.0) * 0.15;
                drop.display().teleport(drop.base().clone().add(0, bob, 0));
            }
            boolean blinking = age > lifetime - 120;
            drop.display().setViewRange(blinking && (age / 6) % 2 == 0 ? 0f : 1f);
            if (age % 10 == 0) {
                drop.base().getWorld().spawnParticle(Particle.DUST, drop.base(), 3, 0.25, 0.25, 0.25, 0,
                        new Particle.DustOptions(drop.type().glow, 1.0f));
            }

            Player taker = null;
            for (Player p : game.aliveOnline()) {
                if (p.getWorld() == drop.base().getWorld() && p.getLocation().add(0, 1, 0).distanceSquared(drop.base()) < 2.6) {
                    taker = p;
                    break;
                }
            }
            if (taker != null) {
                Displays.remove(drop.display());
                it.remove();
                apply(drop.type(), taker);
            }
        }
    }

    private void apply(Type type, Player taker) {
        Title title = Title.title(Msg.mm(type.title), Msg.mm("<gray>" + taker.getName()),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(500)));
        for (Player p : game.online()) {
            p.showTitle(title);
            game.settings().sound("powerup-" + type.configKey()).playTo(p);
        }
        switch (type) {
            case MAX_AMMO -> {
                for (Player p : game.aliveOnline()) plugin.weaponService().refillReserves(p);
            }
            case DOUBLE_POINTS -> {
                doubleUntil = game.tick() + cfg().doublePointsSeconds * 20L;
                game.economy().setMultiplier(2);
            }
            case NUKE -> nuke(taker);
        }
    }

    /** Tous les zombies vivants meurent (sans points d'élimination) et chaque joueur reçoit le bonus de bombe. */
    private void nuke(Player taker) {
        List<Zombie> victims = new ArrayList<>();
        for (Zombie z : taker.getWorld().getEntitiesByClass(Zombie.class)) {
            ZombieManager.ZombieData d = game.zombies().data(z);
            if (d == null || z.isDead()) continue;
            d.lastHitBy = null; // pas de points ni de bonus pour ces morts-là
            victims.add(z);
        }
        for (Zombie z : victims) {
            z.getWorld().spawnParticle(Particle.FLAME, z.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
            z.setHealth(0);
        }
        for (Player p : game.online()) {
            p.spawnParticle(Particle.FLASH, p.getLocation().add(0, 1.6, 0), 1, 0, 0, 0, 0, Color.WHITE);
        }
        for (GamePlayer gp : game.players()) game.economy().reward(gp, cfg().nukePoints);
    }

    void clear() {
        for (Drop d : drops) Displays.remove(d.display());
        drops.clear();
        doubleUntil = -1;
        game.economy().setMultiplier(1);
    }

    /** Ligne pour le tableau latéral, ou null. */
    Component hudLine() {
        int s = doublePointsSeconds();
        return s > 0 ? Msg.mm("<yellow><bold>x2</bold> <gray>points <white>" + s + " s") : null;
    }
}
