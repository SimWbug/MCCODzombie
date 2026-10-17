package fr.cleangang.zombies.listener;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.ui.MapMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    private final ZombiesPlugin plugin;

    public MenuListener(ZombiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof MapMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        String mapId = menu.mapAt(e.getRawSlot());
        if (mapId == null) return;
        p.closeInventory();
        plugin.games().join(p, mapId);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof MapMenu) e.setCancelled(true);
    }
}
