package fr.cleangang.zombies;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Clés de PersistentDataContainer utilisées par le plugin. */
public final class Keys {

    public final NamespacedKey weaponId;
    public final NamespacedKey ammoMagazine;
    public final NamespacedKey ammoReserve;
    public final NamespacedKey zombieType;

    Keys(Plugin plugin) {
        weaponId = new NamespacedKey(plugin, "weapon");
        ammoMagazine = new NamespacedKey(plugin, "ammo_mag");
        ammoReserve = new NamespacedKey(plugin, "ammo_reserve");
        zombieType = new NamespacedKey(plugin, "zombie_type");
    }
}
