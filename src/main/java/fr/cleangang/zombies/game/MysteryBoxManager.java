package fr.cleangang.zombies.game;

import fr.cleangang.zombies.ZombiesPlugin;
import fr.cleangang.zombies.config.WeaponDefinition;
import fr.cleangang.zombies.map.BoxSpot;
import fr.cleangang.zombies.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Boîte mystère : on paie, le coffre s'ouvre, des armes défilent en montant, puis l'arme tirée
 * reste proposée quelques secondes au seul acheteur. Un paiement = un tirage, même en cliquant vite.
 * Seul moyen d'obtenir le pistolet laser.
 */
public final class MysteryBoxManager {

    private enum State {IDLE, ROLLING, OFFERING}

    private static final int ROLL_TICKS = 100;
    private static final int OFFER_TICKS = 200;

    private final ZombiesPlugin plugin;
    private final Game game;
    private final Random rng;

    private State state = State.IDLE;
    private UUID owner;
    private WeaponDefinition reward;
    private int ticks;
    /** Petit délai après fermeture pour qu'un clic maintenu ne relance pas la boîte. */
    private long reopenAfter;
    private ItemDisplay spinning;
    private TextDisplay label;

    MysteryBoxManager(ZombiesPlugin plugin, Game game, Random rng) {
        this.plugin = plugin;
        this.game = game;
        this.rng = rng;
    }

    private BoxSpot spot() {
        return game.arena().box;
    }

    void spawnDisplays() {
        if (spot() == null) return;
        label = Displays.text(plugin.keys(), spot().top().clone().add(0, 0.9, 0), idleLabel());
    }

    private Component idleLabel() {
        return Msg.mm("<light_purple><bold>Boîte mystère</bold>\n<gold><cost> pts",
                Placeholder.unparsed("cost", Msg.formatPoints(spot().def().cost())));
    }

    void clear() {
        Displays.remove(spinning);
        Displays.remove(label);
        spinning = null;
        label = null;
        if (spot() != null) setLid(false);
        state = State.IDLE;
    }

    void use(Player p, GamePlayer gp) {
        BoxSpot spot = spot();
        if (spot == null) return;
        switch (state) {
            case IDLE -> {
                if (game.tick() < reopenAfter) return;
                if (!game.economy().spend(gp, spot.def().cost())) {
                    game.settings().sound("deny").playTo(p);
                    p.sendActionBar(Msg.mm("<red>Il te faut <bold><cost></bold> points",
                            Placeholder.unparsed("cost", Msg.formatPoints(spot.def().cost()))));
                    return;
                }
                owner = p.getUniqueId();
                reward = pick(p);
                ticks = 0;
                state = State.ROLLING;
                setLid(true);
                game.settings().sound("box-open").play(spot.top(), SoundCategory.BLOCKS);
                spinning = Displays.item(plugin.keys(), spot.top().clone().add(0, 0.2, 0),
                        plugin.weaponService().createItem(randomBoxWeapon()), Display.Billboard.FIXED);
                if (label != null) label.text(Msg.mm("<light_purple><player>…", Placeholder.unparsed("player", p.getName())));
            }
            case ROLLING -> p.sendActionBar(Msg.mm("<gray>La boîte tourne…"));
            case OFFERING -> {
                if (!p.getUniqueId().equals(owner)) {
                    p.sendActionBar(Msg.mm("<gray>Cette arme est pour un autre joueur."));
                    return;
                }
                plugin.weaponService().give(p, gp, reward);
                game.settings().sound("purchase").playTo(p);
                game.broadcast("<gold><player> <gray>a obtenu <w> <gray>dans la boîte !",
                        Placeholder.unparsed("player", p.getName()), Placeholder.component("w", Msg.mm(reward.name())));
                close();
            }
        }
    }

    void tick() {
        if (state == State.IDLE || spinning == null) return;
        ticks++;
        Location top = spot().top();
        if (state == State.ROLLING) {
            int interval = 2 + ticks / 20;
            if (ticks % interval == 0) {
                spinning.setItemStack(plugin.weaponService().createItem(randomBoxWeapon()));
                game.settings().sound("box-roll").play(top, SoundCategory.BLOCKS);
            }
            if (ticks % 3 == 0) spinning.teleport(top.clone().add(0, 0.2 + 0.6 * ticks / ROLL_TICKS, 0));
            if (ticks >= ROLL_TICKS) {
                spinning.setItemStack(plugin.weaponService().createItem(reward));
                spinning.setGlowing(true);
                game.settings().sound("box-reward").play(top, SoundCategory.BLOCKS);
                if (label != null) label.text(Msg.mm("<w>\n<gray>Clic droit pour prendre", Placeholder.component("w", Msg.mm(reward.name()))));
                state = State.OFFERING;
                ticks = 0;
            }
        } else if (state == State.OFFERING) {
            // L'arme redescend dans la boîte pendant les 3 dernières secondes
            if (ticks > OFFER_TICKS - 60 && ticks % 3 == 0) {
                double t = (ticks - (OFFER_TICKS - 60)) / 60.0;
                spinning.teleport(top.clone().add(0, 0.8 - 0.6 * t, 0));
            }
            if (ticks >= OFFER_TICKS) close();
        }
    }

    private void close() {
        Displays.remove(spinning);
        spinning = null;
        state = State.IDLE;
        owner = null;
        reward = null;
        reopenAfter = game.tick() + 30;
        setLid(false);
        if (label != null) label.text(idleLabel());
    }

    Component hint(Player p) {
        if (spot() == null) return null;
        return switch (state) {
            case IDLE -> Msg.mm("<gray>Clic droit : boîte mystère <gold>[<cost>]",
                    Placeholder.unparsed("cost", Msg.formatPoints(spot().def().cost())));
            case ROLLING -> Msg.mm("<gray>La boîte tourne…");
            case OFFERING -> p.getUniqueId().equals(owner)
                    ? Msg.mm("<gray>Clic droit : prendre <w>", Placeholder.component("w", Msg.mm(reward.name())))
                    : Msg.mm("<gray>Cette arme est pour un autre joueur.");
        };
    }

    // ── Tirage ───────────────────────────────────────────────────

    private List<WeaponDefinition> boxWeapons() {
        List<WeaponDefinition> out = new ArrayList<>();
        for (WeaponDefinition d : plugin.weapons().values()) if (d.boxWeight() > 0) out.add(d);
        return out;
    }

    private WeaponDefinition randomBoxWeapon() {
        List<WeaponDefinition> all = boxWeapons();
        return all.isEmpty() ? plugin.weapons().get(game.settings().startingWeapon) : all.get(rng.nextInt(all.size()));
    }

    /** Tirage pondéré, sans les armes que le joueur possède déjà. */
    private WeaponDefinition pick(Player p) {
        List<WeaponDefinition> pool = new ArrayList<>();
        int total = 0;
        for (WeaponDefinition d : boxWeapons()) {
            if (plugin.weaponService().slotOf(p, d.id()) >= 0) continue;
            pool.add(d);
            total += d.boxWeight();
        }
        if (pool.isEmpty()) return randomBoxWeapon();
        int r = rng.nextInt(total);
        for (WeaponDefinition d : pool) {
            r -= d.boxWeight();
            if (r < 0) return d;
        }
        return pool.getLast();
    }

    private void setLid(boolean open) {
        BlockState st = spot().chest().getState();
        if (st instanceof Lidded lid) {
            if (open) lid.open();
            else lid.close();
        }
    }
}
