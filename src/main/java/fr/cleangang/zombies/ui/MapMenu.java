package fr.cleangang.zombies.ui;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.game.Game;
import fr.cleangang.zombies.map.MapDefinition;
import fr.cleangang.zombies.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menu de sélection des cartes (4 emplacements sur une ligne). */
public final class MapMenu implements InventoryHolder {

    private static final int[] SLOTS = {10, 12, 14, 16};

    private final Inventory inventory;
    private final Map<Integer, String> slotToMap = new HashMap<>();

    public MapMenu(ZombiesPlugin plugin) {
        inventory = Bukkit.createInventory(this, 27, Msg.mm("<dark_red><bold>Zombies</bold> <dark_gray>— Choisis ta carte"));
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.space());
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        Game current = plugin.games().current();
        int i = 0;
        for (MapDefinition m : plugin.maps().all()) {
            if (i >= SLOTS.length) break;
            inventory.setItem(SLOTS[i], icon(m, current));
            slotToMap.put(SLOTS[i], m.id);
            i++;
        }
    }

    private static ItemStack icon(MapDefinition m, Game current) {
        ItemStack it = new ItemStack(m.icon);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item((m.playable ? "<red><bold>" : "<dark_gray><bold>") + m.displayName));
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.item("<dark_gray>" + m.players + " · Difficulté : <gray>" + m.difficulty));
        lore.add(Component.empty());
        for (String line : m.description) lore.add(Msg.item(line));
        lore.add(Component.empty());
        if (!m.playable) {
            lore.add(Msg.item("<gold>Bientôt disponible"));
        } else if (current == null) {
            lore.add(Msg.item("<green>▶ Clic pour lancer une partie"));
        } else if (current.map().id.equals(m.id) && current.state() == Game.State.LOBBY) {
            lore.add(Msg.item("<green>▶ Clic pour rejoindre <gray>(" + current.playerCount() + " en attente)"));
        } else {
            lore.add(Msg.item("<red>Partie en cours sur " + current.map().displayName));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public String mapAt(int slot) {
        return slotToMap.get(slot);
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
