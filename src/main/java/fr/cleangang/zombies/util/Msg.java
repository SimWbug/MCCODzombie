package fr.cleangang.zombies.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** Petits raccourcis MiniMessage. */
public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_red><bold>Zombies</bold></dark_red> <dark_gray>»</dark_gray> ";

    private Msg() {
    }

    public static Component mm(String text, TagResolver... resolvers) {
        return MM.deserialize(text, resolvers);
    }

    /** Texte d'item : sans l'italique imposé par défaut. */
    public static Component item(String text, TagResolver... resolvers) {
        return MM.deserialize(text, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static void send(CommandSender to, String text, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(PREFIX + text, resolvers));
    }

    public static Component prefixed(String text, TagResolver... resolvers) {
        return MM.deserialize(PREFIX + text, resolvers);
    }

    public static String formatDuration(long millis) {
        long s = millis / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public static String formatPoints(int points) {
        return String.format("%,d", points).replace(',', ' ');
    }
}
