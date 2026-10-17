package fr.cleangang.zombies.listener;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.game.Game;
import fr.cleangang.zombies.game.GamePlayer;
import fr.cleangang.zombies.map.DoorRuntime;
import fr.cleangang.zombies.util.Msg;
import fr.cleangang.zombies.zombie.ZombieManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Tous les événements qui touchent une partie ou le monde de l'arène. */
public final class GameListener implements Listener {

    private final ZombiesPlugin plugin;

    public GameListener(ZombiesPlugin plugin) {
        this.plugin = plugin;
    }

    private Game game(Player p) {
        return plugin.games().gameOf(p);
    }

    private boolean isArena(World w) {
        return w != null && plugin.isReady() && w.getName().equals(plugin.settings().arenaWorld);
    }

    // ── Connexion / déconnexion ─────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Le serveur s'est arrêté (ou a planté) pendant une partie : on rend tout au joueur.
        if (plugin.snapshots().has(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || game(p) != null) return;
                plugin.snapshots().restore(p, true);
                Msg.send(p, "<gray>Ta dernière partie de Zombies a été interrompue : ton inventaire et ta position ont été restaurés.");
            });
        } else if (isArena(p.getWorld()) && game(p) == null && !p.hasPermission("cleanzombies.admin")) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.games().sendToHub(p));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.games().leave(e.getPlayer(), true);
    }

    /** Un autre plugin (ou /hub) sort le joueur du monde de l'arène : il quitte la partie proprement. */
    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = game(p);
        if (g != null && g.has(p) && isArena(e.getFrom()) && !isArena(p.getWorld())) {
            plugin.games().leave(p, false);
        }
    }

    // ── Dégâts ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();

        if (victim instanceof Player p) {
            Game g = game(p);
            if (g == null || !g.has(p)) return;
            GamePlayer gp = g.player(p);
            if (g.state() != Game.State.RUNNING || gp == null || !gp.alive()) {
                e.setCancelled(true);
                return;
            }
            if (e instanceof EntityDamageByEntityEvent by) {
                if (by.getDamager() instanceof Player) {
                    e.setCancelled(true); // pas de tir ami
                    return;
                }
                ZombieManager.ZombieData zd = g.zombies().data(by.getDamager());
                if (zd != null) e.setDamage(zd.type.damage());
            }
            if (p.getHealth() - e.getFinalDamage() <= 0) {
                e.setCancelled(true);
                g.onPlayerDown(p);
            }
            return;
        }

        if (victim instanceof Zombie z && e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player attacker) {
            Game g = game(attacker);
            if (g == null) return;
            if (g.isApplyingDamage()) return; // dégâts déjà calculés par le plugin (tir ou couteau)
            e.setCancelled(true);
            GamePlayer gp = g.player(attacker);
            if (gp == null || !gp.alive() || !g.zombies().isOurs(z)) return;
            // Coup au corps à corps (clic gauche) : couteau
            int now = Bukkit.getCurrentTick();
            if (now < gp.nextMeleeTick) return;
            gp.nextMeleeTick = now + plugin.settings().meleeCooldownTicks;
            plugin.settings().sound("melee").play(attacker.getLocation(), SoundCategory.PLAYERS);
            g.damageZombie(attacker, z, plugin.settings().meleeDamage, false, true);
        }
    }

    @EventHandler
    public void onZombieDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Zombie z)) return;
        Game g = plugin.games().current();
        if (g == null || !g.zombies().isOurs(z)) {
            if (isArena(z.getWorld())) {
                e.getDrops().clear();
                e.setDroppedExp(0);
            }
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        g.onZombieDeath(z);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        // Ne devrait pas arriver (coups fatals interceptés) : filet de sécurité contre la perte d'objets.
        Player p = e.getEntity();
        if (game(p) == null) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (isArena(e.getEntity().getWorld()) && e.getEntity() instanceof Zombie) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        // Nos zombies ne ciblent que des joueurs vivants de la partie (choisis par ZombieManager).
        Game g = plugin.games().current();
        if (g == null || !g.zombies().isOurs(e.getEntity())) return;
        if (e.getTarget() instanceof Player p && (!g.has(p) || !g.player(p).alive())) e.setCancelled(true);
    }

    // ── Actions des joueurs ─────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = game(p);
        if (g == null || !g.has(p)) return;
        e.setCancelled(true); // aucune interaction vanilla dans l'arène (coffres, trappes, labour...)
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        GamePlayer gp = g.player(p);
        if (gp == null || !gp.alive()) return;

        DoorRuntime door = g.arena().doorAt(e.getClickedBlock());
        if (door == null && a == Action.RIGHT_CLICK_AIR) door = g.arena().doorAt(p.getTargetBlockExact(4));
        if (door != null && !door.isOpen()) {
            g.tryOpenDoor(p, door);
            return;
        }
        if (g.state() == Game.State.RUNNING) plugin.weaponService().tryShoot(g, gp, p);
    }

    /** Clic droit directement sur un zombie : on tire aussi. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Game g = game(p);
        if (g == null || !g.has(p)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || g.state() != Game.State.RUNNING) return;
        GamePlayer gp = g.player(p);
        if (gp != null && gp.alive()) plugin.weaponService().tryShoot(g, gp, p);
    }

    /** Touche F : recharger. */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        Game g = game(p);
        if (g == null || !g.has(p)) return;
        e.setCancelled(true);
        GamePlayer gp = g.player(p);
        if (gp != null && gp.alive() && g.state() == Game.State.RUNNING) plugin.weaponService().startReload(g, gp, p);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (game(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && game(p) != null && p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && game(p) != null) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    // ── Protection du monde de l'arène ──────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isArena(e.getBlock().getWorld()) && (game(e.getPlayer()) != null || !e.getPlayer().hasPermission("cleanzombies.admin"))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isArena(e.getBlock().getWorld()) && (game(e.getPlayer()) != null || !e.getPlayer().hasPermission("cleanzombies.admin"))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isArena(e.getLocation().getWorld()) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (isArena(e.getWorld()) && e.toWeatherState()) e.setCancelled(true);
    }
}
