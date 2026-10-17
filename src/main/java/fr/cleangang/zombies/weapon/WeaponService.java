package fr.cleangang.zombies.weapon;

import fr.cleangang.zombies.Keys;
import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.config.WeaponDefinition;
import fr.cleangang.zombies.game.Game;
import fr.cleangang.zombies.game.GamePlayer;
import fr.cleangang.zombies.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Armes à feu en "hitscan" : chaque tir est un rayon instantané depuis les yeux du joueur.
 * Les munitions sont stockées sur l'objet lui-même (PersistentDataContainer), donc une arme garde
 * son chargeur si on change de case.
 */
public final class WeaponService {

    private static final Particle.DustOptions TRAIL = new Particle.DustOptions(Color.fromRGB(255, 225, 150), 0.45f);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(120, 10, 10), 1.2f);

    private final ZombiesPlugin plugin;

    public WeaponService(ZombiesPlugin plugin) {
        this.plugin = plugin;
    }

    private Keys keys() {
        return plugin.keys();
    }

    // ── Objets ───────────────────────────────────────────────────

    public ItemStack createItem(WeaponDefinition def) {
        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item(def.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : def.lore()) lore.add(Msg.item(line));
        meta.lore(lore);
        if (plugin.settings().customModels && def.itemModel() != null) meta.setItemModel(def.itemModel());
        meta.setUnbreakable(true);
        meta.setMaxStackSize(1);
        meta.addItemFlags(ItemFlag.values());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys().weaponId, PersistentDataType.STRING, def.id());
        pdc.set(keys().ammoMagazine, PersistentDataType.INTEGER, def.magazine());
        pdc.set(keys().ammoReserve, PersistentDataType.INTEGER, def.reserve());
        item.setItemMeta(meta);
        return item;
    }

    /** Arme + munitions lues sur l'objet, ou null si ce n'est pas une arme du mode. */
    public WeaponState read(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(keys().weaponId, PersistentDataType.STRING);
        if (id == null) return null;
        WeaponDefinition def = plugin.weapons().get(id);
        if (def == null) return null;
        int mag = pdc.getOrDefault(keys().ammoMagazine, PersistentDataType.INTEGER, 0);
        int reserve = pdc.getOrDefault(keys().ammoReserve, PersistentDataType.INTEGER, 0);
        return new WeaponState(def, mag, reserve);
    }

    private void writeAmmo(Player p, int slot, int mag, int reserve) {
        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || item.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys().ammoMagazine, PersistentDataType.INTEGER, mag);
        pdc.set(keys().ammoReserve, PersistentDataType.INTEGER, reserve);
        item.setItemMeta(meta);
        p.getInventory().setItem(slot, item);
    }

    // ── Tir ──────────────────────────────────────────────────────

    public void tryShoot(Game game, GamePlayer gp, Player p) {
        int slot = p.getInventory().getHeldItemSlot();
        WeaponState st = read(p.getInventory().getItem(slot));
        if (st == null) return;
        int now = Bukkit.getCurrentTick();
        if (now < gp.nextShotTick || gp.reloading()) return;
        WeaponDefinition def = st.def();

        if (st.magazine() <= 0) {
            gp.nextShotTick = now + 6;
            def.emptySound().playTo(p);
            if (st.reserve() > 0) startReload(game, gp, p);
            return;
        }
        gp.nextShotTick = now + def.fireDelayTicks();
        writeAmmo(p, slot, st.magazine() - 1, st.reserve());
        def.fireSound().play(p.getLocation(), SoundCategory.PLAYERS);

        Location eye = p.getEyeLocation();
        for (int i = 0; i < def.pellets(); i++) {
            Vector dir = spread(eye.getDirection(), def.spread());
            fireRay(game, p, eye, dir, def);
        }
        if (st.magazine() - 1 == 0 && st.reserve() > 0) startReload(game, gp, p);
    }

    private void fireRay(Game game, Player shooter, Location eye, Vector dir, WeaponDefinition def) {
        World w = eye.getWorld();
        double blockDist = blockHitDistance(game, eye, dir, def.range());
        RayTraceResult ent = w.rayTraceEntities(eye, dir, Math.min(def.range(), blockDist), 0.15,
                e -> e instanceof Zombie z && game.zombies().isOurs(z) && !z.isDead());
        double hitDist = blockDist;
        if (ent != null && ent.getHitEntity() instanceof Zombie z) {
            Vector hit = ent.getHitPosition();
            hitDist = hit.distance(eye.toVector());
            boolean headshot = hit.getY() >= z.getEyeLocation().getY() - 0.3;
            double dmg = def.damage() * (headshot ? def.headshotMultiplier() : 1.0);
            game.damageZombie(shooter, z, dmg, headshot, false);
            w.spawnParticle(Particle.DUST, hit.toLocation(w), 6, 0.1, 0.1, 0.1, 0, BLOOD);
            plugin.settings().sound("hitmarker").playTo(shooter);
        }
        // Traînée de balle
        double len = Math.min(hitDist, def.range());
        Vector step = dir.clone().multiply(0.6);
        Location cur = eye.clone().add(dir.clone().multiply(0.8));
        for (double d = 0.8; d < len; d += 0.6) {
            w.spawnParticle(Particle.DUST, cur, 1, 0, 0, 0, 0, TRAIL);
            cur.add(step);
        }
    }

    /** Distance jusqu'au premier bloc qui arrête la balle (les planches peuvent être traversées). */
    private double blockHitDistance(Game game, Location eye, Vector dir, double range) {
        World w = eye.getWorld();
        Location start = eye.clone();
        double travelled = 0;
        for (int i = 0; i < 6 && travelled < range; i++) {
            RayTraceResult r = w.rayTraceBlocks(start, dir, range - travelled, FluidCollisionMode.NEVER, true);
            if (r == null || r.getHitBlock() == null) return range;
            double d = r.getHitPosition().distance(start.toVector());
            Block hit = r.getHitBlock();
            if (plugin.settings().shootThroughBarricades && game.arena().isBarricadeCell(hit)) {
                // On reprend le rayon juste après la planche traversée
                Location exit = r.getHitPosition().toLocation(w).add(dir.clone().multiply(0.01));
                Vector smallStep = dir.clone().multiply(0.1);
                double extra = 0.01;
                while (extra < 2.0 && exit.getBlock().equals(hit)) {
                    exit.add(smallStep);
                    extra += 0.1;
                }
                travelled += d + extra;
                start = exit;
                continue;
            }
            return travelled + d;
        }
        return Math.min(range, travelled);
    }

    private static Vector spread(Vector dir, double spread) {
        if (spread <= 0) return dir.clone().normalize();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return dir.clone().add(new Vector(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread)).normalize();
    }

    // ── Rechargement ─────────────────────────────────────────────

    public void startReload(Game game, GamePlayer gp, Player p) {
        int slot = p.getInventory().getHeldItemSlot();
        WeaponState st = read(p.getInventory().getItem(slot));
        if (st == null || gp.reloading()) return;
        WeaponDefinition def = st.def();
        if (st.magazine() >= def.magazine() || st.reserve() <= 0) return;
        def.reloadSound().play(p.getLocation(), SoundCategory.PLAYERS);
        gp.startReload(Bukkit.getScheduler().runTaskLater(plugin, () -> finishReload(game, gp, slot, def.id()), def.reloadTicks()), slot);
    }

    private void finishReload(Game game, GamePlayer gp, int slot, String weaponId) {
        gp.finishReload();
        Player p = gp.player();
        if (p == null || !game.has(p) || !gp.alive()) return;
        if (p.getInventory().getHeldItemSlot() != slot) return; // a changé d'arme : rechargement annulé
        WeaponState st = read(p.getInventory().getItem(slot));
        if (st == null || !st.def().id().equals(weaponId)) return;
        int fill = Math.min(st.def().magazine() - st.magazine(), st.reserve());
        if (fill > 0) writeAmmo(p, slot, st.magazine() + fill, st.reserve() - fill);
    }

    /** Ligne de munitions pour la barre d'action. */
    public Component ammoLine(GamePlayer gp, Player p) {
        WeaponState st = read(p.getInventory().getItemInMainHand());
        if (st == null) return Component.empty();
        if (gp.reloading()) return Msg.mm("<yellow>Rechargement…");
        String magColor = st.magazine() == 0 ? "red" : st.magazine() <= Math.max(1, st.def().magazine() / 4) ? "gold" : "white";
        return Msg.mm("<" + magColor + "><bold>" + st.magazine() + "</bold></" + magColor + "> <dark_gray>/</dark_gray> <gray>" + st.reserve());
    }

    public record WeaponState(WeaponDefinition def, int magazine, int reserve) {
    }
}
