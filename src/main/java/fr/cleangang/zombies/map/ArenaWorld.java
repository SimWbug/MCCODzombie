package fr.cleangang.zombies.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

/** Monde vide réservé au mode Zombies. Toujours de nuit, sans météo ni apparition naturelle (voir GameListener). */
public final class ArenaWorld {

    public static final long NIGHT = 18_000L;

    private ArenaWorld() {
    }

    public static World getOrCreate(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(new VoidGenerator())
                    .generateStructures(false)
                    .createWorld();
        }
        if (w != null) {
            w.setTime(NIGHT);
            w.setStorm(false);
            w.setThundering(false);
        }
        return w;
    }

    /** Générateur qui ne produit rien : toutes les étapes de génération par défaut sont désactivées. */
    static final class VoidGenerator extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
