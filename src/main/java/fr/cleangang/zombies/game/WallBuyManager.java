package fr.cleangang.zombies.game;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.config.WeaponDefinition;
import fr.cleangang.zombies.map.WallBuySpot;
import fr.cleangang.zombies.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Armes murales : l'arme est exposée devant un bloc repère du mur, avec son nom et son prix.
 * Clic droit en visant le mur : achat de l'arme, ou de ses munitions si on l'a déjà.
 */
public final class WallBuyManager {

    private final ZombiesPlugin plugin;
    private final Game game;
    private final List<Entity> entities = new ArrayList<>();

    WallBuyManager(ZombiesPlugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    void spawnDisplays() {
        for (WallBuySpot spot : game.arena().wallBuys) {
            WeaponDefinition def = plugin.weapons().get(spot.def().weapon());
            if (def == null) continue;
            entities.add(Displays.item(plugin.keys(), spot.display(), plugin.weaponService().createItem(def), Display.Billboard.FIXED));
            Component label = Msg.mm("<w>\n<gold><cost> pts", Placeholder.component("w", Msg.mm(def.name())),
                    Placeholder.unparsed("cost", Msg.formatPoints(def.cost())));
            entities.add(Displays.text(plugin.keys(), spot.display().clone().add(0, 0.55, 0), label));
        }
    }

    void clear() {
        entities.forEach(Displays::remove);
        entities.clear();
    }

    /** Achat (arme ou munitions). */
    void buy(Player p, GamePlayer gp, WallBuySpot spot) {
        WeaponDefinition def = plugin.weapons().get(spot.def().weapon());
        if (def == null) return;
        int owned = plugin.weaponService().slotOf(p, def.id());

        if (owned >= 0) {
            if (!def.isFirearm()) {
                p.sendActionBar(Msg.mm("<gray>Tu as déjà le <white><w>", Placeholder.component("w", Msg.mm(def.name()))));
                return;
            }
            var st = plugin.weaponService().read(p.getInventory().getItem(owned));
            if (st == null || (st.magazine() >= def.magazine() && st.reserve() >= def.reserve())) {
                p.sendActionBar(Msg.mm("<gray>Munitions déjà pleines"));
                return;
            }
            if (!pay(p, gp, def.ammoCost())) return;
            plugin.weaponService().refill(p, owned);
            game.settings().sound("purchase").playTo(p);
            p.sendActionBar(Msg.mm("<green>Munitions rechargées"));
            return;
        }
        if (!pay(p, gp, def.cost())) return;
        plugin.weaponService().give(p, gp, def);
        game.settings().sound("purchase").playTo(p);
        game.broadcast("<gold><player> <gray>a acheté : <w>",
                Placeholder.unparsed("player", p.getName()), Placeholder.component("w", Msg.mm(def.name())));
    }

    private boolean pay(Player p, GamePlayer gp, int cost) {
        if (game.economy().spend(gp, cost)) return true;
        game.settings().sound("deny").playTo(p);
        p.sendActionBar(Msg.mm("<red>Il te faut <bold><cost></bold> points", Placeholder.unparsed("cost", Msg.formatPoints(cost))));
        return false;
    }

    /** Texte d'aide quand le joueur vise le mur. */
    Component hint(Player p, WallBuySpot spot) {
        WeaponDefinition def = plugin.weapons().get(spot.def().weapon());
        if (def == null) return null;
        int owned = plugin.weaponService().slotOf(p, def.id());
        if (owned >= 0 && !def.isFirearm()) return Msg.mm("<gray>Déjà acheté : <w>", Placeholder.component("w", Msg.mm(def.name())));
        if (owned >= 0) {
            return Msg.mm("<gray>Clic droit : munitions <w> <gold>[<cost>]",
                    Placeholder.component("w", Msg.mm(def.name())), Placeholder.unparsed("cost", Msg.formatPoints(def.ammoCost())));
        }
        return Msg.mm("<gray>Clic droit : acheter <w> <gold>[<cost>]",
                Placeholder.component("w", Msg.mm(def.name())), Placeholder.unparsed("cost", Msg.formatPoints(def.cost())));
    }

}
