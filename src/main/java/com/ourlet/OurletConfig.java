package com.ourlet;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Réglages du serveur : config/ourlet.properties (un simple fichier texte, le même pour toutes
 * les versions de Minecraft). Relu à chaque démarrage du serveur ; les clés absentes sont ajoutées.
 */
public final class OurletConfig {

    private OurletConfig() {}

    public static int maxFolds = 12;
    public static int pocketSeconds = 20;
    public static boolean crossDimension = true;
    public static boolean forceLoad = true;
    public static boolean protect = true;
    public static boolean relay = true;
    public static boolean pistons = true;
    public static boolean gaze = true;
    public static Set<String> blacklist = new HashSet<>(Set.of("minecraft:ender_dragon", "minecraft:wither"));

    private static final String HEADER = """
        Ourlet : reglages du serveur / server settings
        max_folds_per_player : plis permanents par joueur / permanent folds per player
        pocket_seconds : duree d'un pli de poche (de a coudre) / pocket fold duration (thimble)
        cross_dimension : plis entre deux dimensions / folds across dimensions
        force_load_chunks : garder charges les deux bouts d'un pli / keep both ends of a fold loaded
        protect_folds : seuls le proprietaire et les operateurs decousent ou coupent un pli / only owner and ops may unpick or snip
        relay : miner, poser et utiliser a travers une couture / mine, place and use through a seam
        pistons : les pistons poussent des blocs a travers les plis / pistons push blocks through folds
        gaze : viser une couture affiche sa destination / aiming at a seam shows where it leads
        blacklist : entites qui ne passent jamais (separees par des virgules) / entities that never pass (comma separated)""";

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("ourlet.properties");
    }

    public static void load() {
        Path file = file();
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                Ourlet.LOGGER.warn("Lecture de {} impossible, réglages par défaut", file, e);
            }
        }
        maxFolds = integer(p, "max_folds_per_player", 12, 0, 10_000);
        pocketSeconds = integer(p, "pocket_seconds", 20, 1, 3600);
        crossDimension = bool(p, "cross_dimension", true);
        forceLoad = bool(p, "force_load_chunks", true);
        protect = bool(p, "protect_folds", true);
        relay = bool(p, "relay", true);
        pistons = bool(p, "pistons", true);
        gaze = bool(p, "gaze", true);
        String bl = p.getProperty("blacklist", "minecraft:ender_dragon,minecraft:wither");
        p.setProperty("blacklist", bl);
        blacklist = new HashSet<>();
        for (String s : bl.split(",")) if (!s.isBlank()) blacklist.add(s.trim());
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(w, HEADER);
            }
        } catch (IOException e) {
            Ourlet.LOGGER.warn("Écriture de {} impossible", file, e);
        }
    }

    private static int integer(Properties p, String key, int def, int min, int max) {
        int v = def;
        try {
            v = Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
        } catch (NumberFormatException ignored) {
            // valeur invalide : on garde la valeur par défaut
        }
        v = Math.max(min, Math.min(max, v));
        p.setProperty(key, String.valueOf(v));
        return v;
    }

    private static boolean bool(Properties p, String key, boolean def) {
        boolean v = Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)).trim());
        p.setProperty(key, String.valueOf(v));
        return v;
    }
}
