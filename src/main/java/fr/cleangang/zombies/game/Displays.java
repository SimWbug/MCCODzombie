package fr.cleangang.zombies.game;

import fr.cleangang.zombies.Keys;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * Entités d'affichage (armes au mur, arme de la boîte, bonus). Elles ne sont jamais sauvegardées
 * avec le monde et portent une marque pour être retirées même après un arrêt brutal.
 */
final class Displays {

    private Displays() {
    }

    static ItemDisplay item(Keys keys, Location at, ItemStack item, Display.Billboard billboard) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(billboard);
            d.setPersistent(false);
            d.setTeleportDuration(3);
            d.setInterpolationDuration(3);
            d.getPersistentDataContainer().set(keys.display, PersistentDataType.BYTE, (byte) 1);
        });
    }

    static TextDisplay text(Keys keys, Location at, Component text) {
        return at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(keys.display, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Retire toutes nos entités d'affichage restées dans une zone (ex. après un crash). */
    static void purge(Keys keys, World world, BoundingBox area) {
        for (Entity e : world.getNearbyEntities(area)) {
            if (e.getPersistentDataContainer().has(keys.display, PersistentDataType.BYTE)) e.remove();
        }
    }

    static void remove(Entity e) {
        if (e != null && e.isValid()) e.remove();
    }
}
