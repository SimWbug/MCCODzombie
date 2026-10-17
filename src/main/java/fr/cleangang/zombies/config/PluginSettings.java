package fr.cleangang.zombies.config;

import fr.cleangang.zombies.util.SoundRef;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Valeurs de config.yml, lues une fois et immuables. */
public final class PluginSettings {

    public final String arenaWorld;
    public final int originX, originY, originZ;
    public final String hubCommand;

    public final String defaultMap;
    public final int minPlayers, maxPlayers, lobbyCountdownSeconds, fullLobbyCountdownSeconds;

    public final Rounds rounds;
    public final List<ZombieTypeDef> zombieTypes;
    public final ZombieTypeDef standardZombie;
    public final Points points;
    public final double playerMaxHealth;
    public final boolean respawnNextRound;
    public final String startingWeapon;
    public final double meleeDamage;
    public final int meleeCooldownTicks;
    public final int tearTicks, repairTicks;
    public final double repairRange;
    public final boolean shootThroughBarricades;
    public final int stuckTimeoutTicks;
    public final boolean customModels;
    public final Pack pack;
    public final Map<String, SoundRef> sounds;

    private PluginSettings(FileConfiguration c, Cfg cfg) {
        ConfigurationSection arena = cfg.section(c, "arena");
        arenaWorld = arena == null ? "cg_zombies" : arena.getString("world", "cg_zombies");
        int[] origin = arena == null ? new int[]{0, 64, 0} : cfg.vec3(arena, "origin");
        originX = origin[0];
        originY = origin[1];
        originZ = origin[2];
        hubCommand = c.getString("hub-command", "").trim();

        ConfigurationSection lobby = cfg.section(c, "lobby");
        defaultMap = lobby == null ? "bunker" : lobby.getString("default-map", "bunker");
        minPlayers = cfg.integer(lobby, "min-players", 1, 1, 4);
        maxPlayers = cfg.integer(lobby, "max-players", 4, 1, 4);
        lobbyCountdownSeconds = cfg.integer(lobby, "countdown-seconds", 15, 1, 600);
        fullLobbyCountdownSeconds = cfg.integer(lobby, "full-countdown-seconds", 5, 1, 600);
        if (minPlayers > maxPlayers) cfg.error("lobby.min-players", "ne peut pas dépasser max-players");

        rounds = new Rounds(cfg.section(c, "rounds"), cfg);

        List<ZombieTypeDef> types = new ArrayList<>();
        ZombieTypeDef standard = null;
        ConfigurationSection zt = cfg.section(c, "zombie-types");
        if (zt != null) {
            for (String id : zt.getKeys(false)) {
                ZombieTypeDef def = ZombieTypeDef.parse(id, zt.getConfigurationSection(id), cfg);
                if (id.equals("standard")) standard = def;
                else types.add(def);
            }
        }
        if (standard == null) {
            cfg.error("zombie-types.standard", "type obligatoire manquant");
            standard = new ZombieTypeDef("standard", 1, 0, 0, 0, 0.23, 7, 1, 0.4, Color.fromRGB(0x4B5320));
        }
        standardZombie = standard;
        zombieTypes = Collections.unmodifiableList(types);

        points = new Points(cfg.section(c, "points"), cfg);

        ConfigurationSection player = cfg.section(c, "player");
        playerMaxHealth = cfg.number(player, "max-health", 20, 2, 1024);
        respawnNextRound = player == null || player.getBoolean("respawn-next-round", true);
        startingWeapon = player == null ? "pistol" : player.getString("starting-weapon", "pistol");

        ConfigurationSection melee = cfg.section(c, "melee");
        meleeDamage = cfg.number(melee, "damage", 5, 0, 10_000);
        meleeCooldownTicks = cfg.integer(melee, "cooldown-ticks", 12, 0, 200);

        ConfigurationSection bar = cfg.section(c, "barricades");
        tearTicks = cfg.integer(bar, "tear-ticks", 30, 1, 1200);
        repairTicks = cfg.integer(bar, "repair-ticks", 12, 1, 1200);
        repairRange = cfg.number(bar, "repair-range", 2.5, 0.5, 10);
        shootThroughBarricades = bar == null || bar.getBoolean("shoot-through", true);

        ConfigurationSection zombies = cfg.section(c, "zombies");
        stuckTimeoutTicks = cfg.integer(zombies, "stuck-timeout-ticks", 400, 60, 12_000);

        customModels = c.getBoolean("items.custom-models", true);
        pack = new Pack(cfg.section(c, "resource-pack"), cfg);

        Map<String, SoundRef> snd = new HashMap<>();
        ConfigurationSection sounds = c.getConfigurationSection("sounds");
        if (sounds != null) {
            for (String key : sounds.getKeys(false)) snd.put(key, SoundRef.parse(sounds, key, SoundRef.NONE));
        }
        this.sounds = Collections.unmodifiableMap(snd);
    }

    public static PluginSettings load(FileConfiguration c, List<String> errors) {
        return new PluginSettings(c, new Cfg("config.yml", errors));
    }

    public SoundRef sound(String key) {
        return sounds.getOrDefault(key, SoundRef.NONE);
    }

    // ───────────────────────────────────────────────────────────

    public static final class Rounds {
        public final int intermissionSeconds, maxRound;
        public final double countBase, countLinear, countQuadratic, countExtraPerPlayer;
        public final int countCap;
        public final double healthBase, healthLinear, healthMultiplier;
        public final int healthLinearUntil;
        public final int spawnStartTicks, spawnMinTicks, spawnDecay;
        public final int maxAliveBase, maxAlivePerExtraPlayer, maxAliveCap;

        Rounds(ConfigurationSection s, Cfg cfg) {
            intermissionSeconds = cfg.integer(s, "intermission-seconds", 10, 0, 600);
            maxRound = cfg.integer(s, "max-round", 0, 0, 10_000);
            ConfigurationSection count = s == null ? null : s.getConfigurationSection("count");
            countBase = cfg.number(count, "base", 6, 1, 10_000);
            countLinear = cfg.number(count, "linear", 2, 0, 10_000);
            countQuadratic = cfg.number(count, "quadratic", 0.12, 0, 1_000);
            countExtraPerPlayer = cfg.number(count, "extra-per-player", 0.5, 0, 10);
            countCap = cfg.integer(count, "cap", 250, 1, 100_000);
            ConfigurationSection health = s == null ? null : s.getConfigurationSection("health");
            healthBase = cfg.number(health, "base", 8, 1, 1_000_000);
            healthLinear = cfg.number(health, "linear", 4, 0, 1_000_000);
            healthLinearUntil = cfg.integer(health, "linear-until", 9, 1, 10_000);
            healthMultiplier = cfg.number(health, "multiplier", 1.1, 1, 10);
            ConfigurationSection spawn = s == null ? null : s.getConfigurationSection("spawn-interval");
            spawnStartTicks = cfg.integer(spawn, "start-ticks", 40, 1, 1200);
            spawnMinTicks = cfg.integer(spawn, "min-ticks", 8, 1, 1200);
            spawnDecay = cfg.integer(spawn, "decay-per-round", 3, 0, 1200);
            ConfigurationSection alive = s == null ? null : s.getConfigurationSection("max-alive");
            maxAliveBase = cfg.integer(alive, "base", 10, 1, 500);
            maxAlivePerExtraPlayer = cfg.integer(alive, "per-extra-player", 4, 0, 500);
            maxAliveCap = cfg.integer(alive, "cap", 28, 1, 500);
        }

        public int zombieCount(int round, int players) {
            double r = round - 1;
            double raw = (countBase + countLinear * r + countQuadratic * r * r) * (1 + countExtraPerPlayer * Math.max(0, players - 1));
            return (int) Math.min(countCap, Math.max(1, Math.round(raw)));
        }

        public double zombieHealth(int round) {
            if (round <= healthLinearUntil) return healthBase + healthLinear * (round - 1);
            double atCap = healthBase + healthLinear * (healthLinearUntil - 1);
            return Math.min(1_000_000, atCap * Math.pow(healthMultiplier, round - healthLinearUntil));
        }

        public int spawnInterval(int round) {
            return Math.max(spawnMinTicks, spawnStartTicks - spawnDecay * (round - 1));
        }

        public int maxAlive(int players) {
            return Math.min(maxAliveCap, maxAliveBase + maxAlivePerExtraPlayer * Math.max(0, players - 1));
        }
    }

    public static final class Points {
        public final int start, hit, kill, headshotBonus, meleeKillBonus, repair, repairCapPerWindow;

        Points(ConfigurationSection s, Cfg cfg) {
            start = cfg.integer(s, "start", 500, 0, 1_000_000);
            hit = cfg.integer(s, "hit", 10, 0, 100_000);
            kill = cfg.integer(s, "kill", 50, 0, 100_000);
            headshotBonus = cfg.integer(s, "headshot-bonus", 50, 0, 100_000);
            meleeKillBonus = cfg.integer(s, "melee-kill-bonus", 80, 0, 100_000);
            repair = cfg.integer(s, "repair", 10, 0, 100_000);
            repairCapPerWindow = cfg.integer(s, "repair-cap-per-window", 50, 0, 1_000_000);
        }
    }

    public static final class Pack {
        public final String url, sha1, prompt;
        public final boolean required, unloadOnLeave;
        public final int timeoutSeconds;

        Pack(ConfigurationSection s, Cfg cfg) {
            url = s == null ? "" : s.getString("url", "").trim();
            sha1 = s == null ? "" : s.getString("sha1", "").trim().toLowerCase();
            prompt = s == null ? "" : s.getString("prompt", "");
            required = s == null || s.getBoolean("required", true);
            unloadOnLeave = s == null || s.getBoolean("unload-on-leave", true);
            timeoutSeconds = cfg.integer(s, "timeout-seconds", 60, 5, 600);
            if (!url.isEmpty() && !url.startsWith("http")) cfg.error("resource-pack.url", "doit commencer par http(s)://");
            if (!sha1.isEmpty() && !sha1.matches("[0-9a-f]{40}")) cfg.error("resource-pack.sha1", "doit faire 40 caractères hexadécimaux");
        }

        public boolean enabled() {
            return !url.isEmpty();
        }
    }
}
