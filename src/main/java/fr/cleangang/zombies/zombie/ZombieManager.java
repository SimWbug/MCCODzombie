package fr.cleangang.zombies.zombie;

import com.destroystokyo.paper.entity.ai.GoalType;
import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.config.PluginSettings;
import fr.cleangang.zombies.config.ZombieTypeDef;
import fr.cleangang.zombies.game.Game;
import fr.cleangang.zombies.map.Barricade;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Apparition, déplacement et nettoyage des zombies d'une partie.
 * <p>
 * Les objectifs de ciblage vanilla sont retirés : le plugin décide seul de la cible.
 * Tant qu'un zombie est dehors, il marche jusqu'à sa fenêtre et arrache les planches ;
 * une fois entré, il poursuit le joueur vivant le plus proche.
 */
public final class ZombieManager {

    /** Fréquence de la logique zombie (ticks). */
    public static final int TICK_PERIOD = 10;

    private final ZombiesPlugin plugin;
    private final Game game;
    private final Random rng;
    private final Map<UUID, ZombieData> zombies = new HashMap<>();

    public ZombieManager(ZombiesPlugin plugin, Game game, Random rng) {
        this.plugin = plugin;
        this.game = game;
        this.rng = rng;
    }

    public int alive() {
        return zombies.size();
    }

    public boolean isOurs(Entity e) {
        return e != null && zombies.containsKey(e.getUniqueId());
    }

    public ZombieData data(Entity e) {
        return e == null ? null : zombies.get(e.getUniqueId());
    }

    /** Fait apparaître un zombie derrière une fenêtre active. Renvoie false si aucune fenêtre n'est disponible. */
    public boolean spawnOne(int round) {
        List<Barricade> active = game.arena().activeBarricades();
        if (active.isEmpty()) return false;
        Barricade b = active.get(rng.nextInt(active.size()));
        ZombieTypeDef type = chooseType(round);
        double health = Math.max(1, plugin.settings().rounds.zombieHealth(round) * type.healthMultiplier());

        Location loc = b.spawn.clone().add((rng.nextDouble() - 0.5) * 0.6, 0, (rng.nextDouble() - 0.5) * 0.6);
        // randomizeData=false : pas de bébé, pas d'équipement aléatoire, pas de chevaucheur de poulet
        Zombie z = loc.getWorld().spawn(loc, Zombie.class, false, zombie -> setup(zombie, type, health));
        zombies.put(z.getUniqueId(), new ZombieData(z, type, b, game.tick()));
        return true;
    }

    private void setup(Zombie z, ZombieTypeDef type, double health) {
        z.setAdult();
        z.setCanPickupItems(false);
        z.setRemoveWhenFarAway(false);
        z.setPersistent(false);
        z.setMaximumNoDamageTicks(0);
        setAttr(z, Attribute.MAX_HEALTH, health);
        z.setHealth(health);
        setAttr(z, Attribute.MOVEMENT_SPEED, type.speed());
        setAttr(z, Attribute.ATTACK_DAMAGE, type.damage());
        setAttr(z, Attribute.FOLLOW_RANGE, 64);
        setAttr(z, Attribute.KNOCKBACK_RESISTANCE, type.knockbackResistance());

        EntityEquipment eq = z.getEquipment();
        eq.clear();
        eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, type));
        eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, type));
        eq.setBoots(dyed(Material.LEATHER_BOOTS, type));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            eq.setDropChance(slot, 0f);
        }
        z.getPersistentDataContainer().set(plugin.keys().zombieType, PersistentDataType.STRING, type.id());
        Bukkit.getMobGoals().removeAllGoals(z, GoalType.TARGET);
    }

    private static ItemStack dyed(Material m, ZombieTypeDef type) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(type.armorColor());
            it.setItemMeta(meta);
        }
        return it;
    }

    private static void setAttr(Zombie z, Attribute attr, double value) {
        AttributeInstance inst = z.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private ZombieTypeDef chooseType(int round) {
        PluginSettings s = plugin.settings();
        for (ZombieTypeDef t : s.zombieTypes) {
            if (rng.nextDouble() < t.chanceAt(round)) return t;
        }
        return s.standardZombie;
    }

    /** Appelé toutes les {@link #TICK_PERIOD} ticks. Renvoie le nombre de zombies retirés (à remettre en file). */
    public int tick(List<Player> targets) {
        PluginSettings s = plugin.settings();
        long now = game.tick();
        int requeue = 0;
        List<UUID> toRemove = new ArrayList<>();

        for (ZombieData d : zombies.values()) {
            Zombie z = d.zombie;
            if (!z.isValid()) {
                // Disparu sans mourir (déchargement, /kill d'un autre plugin...) : on le remplace.
                if (!z.isDead()) requeue++;
                toRemove.add(z.getUniqueId());
                continue;
            }
            Location loc = z.getLocation();

            // Hors carte : on le retire et on le remplace
            if (!game.arena().contains(loc, 3)) {
                z.remove();
                toRemove.add(z.getUniqueId());
                requeue++;
                continue;
            }

            Barricade b = d.window;
            if (!d.inside) {
                if (!b.isOpen()) {
                    if (loc.distanceSquared(b.outsideAnchor) < 2.6) {
                        d.tearProgress += TICK_PERIOD;
                        d.markProgress(loc, now);
                        if (d.tearProgress >= s.tearTicks) {
                            d.tearProgress = 0;
                            if (b.tear()) s.sound("tear").play(b.outsideAnchor, SoundCategory.HOSTILE);
                        }
                    } else {
                        z.setTarget(null);
                        z.getPathfinder().moveTo(b.outsideAnchor);
                    }
                } else if (loc.distanceSquared(b.insideAnchor) < 2.25) {
                    d.inside = true;
                } else {
                    z.setTarget(null);
                    z.getPathfinder().moveTo(b.insideAnchor);
                }
            }
            if (d.inside) {
                Player target = nearest(loc, targets);
                if (target != null && z.getTarget() != target) z.setTarget(target);
            }

            // Bloqué trop longtemps → retiré et remplacé
            if (loc.distanceSquared(d.lastPos) > 1.0) d.markProgress(loc, now);
            if (now - d.lastProgressTick > s.stuckTimeoutTicks) {
                z.remove();
                toRemove.add(z.getUniqueId());
                requeue++;
            }
        }
        toRemove.forEach(zombies::remove);
        return requeue;
    }

    private static Player nearest(Location from, List<Player> targets) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : targets) {
            if (p.getWorld() != from.getWorld()) continue;
            double d = p.getLocation().distanceSquared(from);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Retire le zombie mort de la liste et renvoie ses données (null si ce n'était pas un des nôtres). */
    public ZombieData onDeath(Zombie z) {
        return zombies.remove(z.getUniqueId());
    }

    public void clearAll() {
        for (ZombieData d : zombies.values()) {
            if (d.zombie.isValid()) d.zombie.remove();
        }
        zombies.clear();
    }

    /** Données d'un zombie vivant. */
    public static final class ZombieData {
        public final Zombie zombie;
        public final ZombieTypeDef type;
        final Barricade window;
        boolean inside;
        int tearProgress;
        Location lastPos;
        long lastProgressTick;
        // Dernier coup reçu d'un joueur (pour attribuer les points du kill)
        public UUID lastHitBy;
        public boolean lastHitHeadshot;
        public boolean lastHitMelee;
        public long lastHitTick;

        ZombieData(Zombie zombie, ZombieTypeDef type, Barricade window, long tick) {
            this.zombie = zombie;
            this.type = type;
            this.window = window;
            this.lastPos = zombie.getLocation();
            this.lastProgressTick = tick;
        }

        void markProgress(Location loc, long tick) {
            lastPos = loc;
            lastProgressTick = tick;
        }
    }
}
