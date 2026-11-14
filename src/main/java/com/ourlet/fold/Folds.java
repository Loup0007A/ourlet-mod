package com.ourlet.fold;

import com.ourlet.Ourlet;
import com.ourlet.platform.Compat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Tous les plis du serveur : création, découture, sauvegarde (un simple fichier texte dans le
 * dossier du monde, identique pour toutes les versions de Minecraft) et entretien à chaque tick.
 * Les deux bouts d'un pli peuvent être dans deux dimensions différentes.
 */
public final class Folds {

    private Folds() {}

    /** Plis permanents par joueur. */
    public static final int MAX_PER_PLAYER = 12;
    /** Durée d'un pli de poche (dé à coudre), en ticks. */
    public static final int POCKET_TICKS = 20 * 20;

    /** Un bout de pli vu depuis une face : {@code here} est la face touchée, {@code there} l'autre. */
    public record End(Fold fold, Anchor here) {
        public Anchor there() {
            return fold.other(here);
        }

        public boolean open() {
            return fold.canEnter(here);
        }
    }

    /** Premier point posé avec l'aiguille, en attente du second. */
    public record Pending(Anchor anchor, long time) {}

    private static final List<Fold> ALL = new ArrayList<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** La « maison » de chaque joueur, pour le dé à coudre. */
    private static final Map<UUID, Anchor> HOMES = new HashMap<>();
    private static MinecraftServer server;
    private static Path file;
    private static int nextId = 1;

    // ───────────────────────── cycle de vie ─────────────────────────

    public static void load(MinecraftServer s) {
        server = s;
        ALL.clear();
        PENDING.clear();
        HOMES.clear();
        nextId = 1;
        file = s.getWorldPath(LevelResource.ROOT).resolve("data").resolve("ourlet_folds.txt");
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    if (line.startsWith("H;")) {
                        String[] p = line.split(";");
                        HOMES.put(UUID.fromString(p[1]), Anchor.parse(p[2], "minecraft:overworld"));
                        continue;
                    }
                    Fold f = Fold.parse(line);
                    ALL.add(f);
                    nextId = Math.max(nextId, f.id + 1);
                } catch (RuntimeException e) {
                    Ourlet.LOGGER.warn("Ligne illisible ignorée : {}", line);
                }
            }
            Ourlet.LOGGER.info("{} pli(s) chargé(s)", ALL.size());
        } catch (IOException e) {
            Ourlet.LOGGER.error("Lecture de {} impossible", file, e);
        }
    }

    public static void save() {
        if (file == null) return;
        List<String> lines = new ArrayList<>();
        lines.add("# Ourlet : un pli par ligne (id;propriétaire;dimension;A;B;couleur;forçageA;forçageB;mode), H;joueur;maison");
        for (Fold f : ALL) if (!f.temporary()) lines.add(f.serialize());
        HOMES.forEach((uuid, a) -> lines.add("H;" + uuid + ";" + a.serialize()));
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Ourlet.LOGGER.error("Écriture de {} impossible", file, e);
        }
    }

    public static void unload() {
        // les plis de poche ne survivent pas à l'arrêt : on les défait proprement (tronçons, coutures)
        for (Fold f : new ArrayList<>(ALL)) if (f.temporary()) remove(f, false);
        save();
        ALL.clear();
        PENDING.clear();
        HOMES.clear();
        server = null;
        file = null;
        Transit.reset();
        Relay.reset();
    }

    // ───────────────────────── recherche ─────────────────────────

    public static MinecraftServer server() {
        return server;
    }

    /** Le monde d'une dimension, ou null si elle n'existe pas (plus). */
    public static ServerLevel level(String dim) {
        if (server == null) return null;
        for (ServerLevel l : server.getAllLevels()) if (Compat.dim(l).equals(dim)) return l;
        return null;
    }

    public static ServerLevel level(Anchor a) {
        return level(a.dim());
    }

    public static List<Fold> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Les plis dont au moins un bout est dans ce monde. */
    public static List<Fold> in(Level level) {
        String dim = Compat.dim(level);
        List<Fold> out = new ArrayList<>();
        for (Fold f : ALL) if (f.a.dim().equals(dim) || f.b.dim().equals(dim)) out.add(f);
        return out;
    }

    public static End find(Level level, BlockPos pos, Direction face) {
        if (ALL.isEmpty()) return null;
        String dim = Compat.dim(level);
        for (Fold f : ALL) {
            if (f.a.is(dim, pos, face)) return new End(f, f.a);
            if (f.b.is(dim, pos, face)) return new End(f, f.b);
        }
        return null;
    }

    public static Fold byId(int id) {
        for (Fold f : ALL) if (f.id == id) return f;
        return null;
    }

    public static int owned(UUID owner) {
        int n = 0;
        for (Fold f : ALL) if (f.owner.equals(owner) && !f.temporary()) n++;
        return n;
    }

    public static Pending pending(UUID player) {
        return PENDING.get(player);
    }

    public static void setPending(UUID player, Pending p) {
        if (p == null) PENDING.remove(player);
        else PENDING.put(player, p);
    }

    public static Anchor home(UUID player) {
        return HOMES.get(player);
    }

    public static void setHome(UUID player, Anchor home) {
        HOMES.put(player, home);
        save();
    }

    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public static Anchor anchor(Level level, BlockPos pos, Direction face, Direction facing) {
        return new Anchor(Compat.dim(level), pos.immutable(), face, Anchor.upFor(face, facing), blockId(level.getBlockState(pos)));
    }

    /** Le bloc d'un bout est-il toujours celui qui a été cousu ? (vrai si son tronçon n'est pas chargé) */
    public static boolean intact(Anchor a) {
        ServerLevel level = level(a);
        if (level == null) return false;
        if (!level.isLoaded(a.pos())) return true;
        BlockState s = level.getBlockState(a.pos());
        return !s.isAir() && blockId(s).equals(a.block());
    }

    // ───────────────────────── couture / découture ─────────────────────────

    public static Fold create(UUID owner, Anchor a, Anchor b) {
        return create(owner, a, b, -1);
    }

    /** {@code lifetime} > 0 : pli de poche, défait au bout de ce nombre de ticks. */
    public static Fold create(UUID owner, Anchor a, Anchor b, int lifetime) {
        int id = nextId++;
        Fold f = new Fold(id, owner, a, b, lifetime > 0 ? Fold.BASTING : Math.floorMod(id - 1, 4));
        if (lifetime > 0) f.expires = server.overworld().getGameTime() + lifetime;
        f.forcedA = force(level(a), a.pos());
        f.forcedB = force(level(b), b.pos());
        ALL.add(f);
        if (!f.temporary()) save();
        f.powered = poweredNow(f);
        Markers.place(f);
        Effects.stitch(level(a), f.a, f.color);
        Effects.stitch(level(b), f.b, f.color);
        Ourlet.LOGGER.info("Pli #{} cousu : {} {} {} <-> {} {} {}{}", f.id, a.dim(), a.pos().toShortString(), a.face(),
            b.dim(), b.pos().toShortString(), b.face(), f.temporary() ? " (de poche)" : "");
        return f;
    }

    public static void remove(Fold f, boolean torn) {
        if (!ALL.remove(f)) return;
        Markers.remove(f);
        unforce(f.a, f.forcedA);
        unforce(f.b, f.forcedB);
        ServerLevel la = level(f.a), lb = level(f.b);
        if (la != null) Effects.unstitch(la, f.a, f.color, torn);
        if (lb != null) Effects.unstitch(lb, f.b, f.color, torn);
        if (!f.temporary()) save();
        Ourlet.LOGGER.info("Pli #{} {}", f.id, torn ? "déchiré (bloc disparu)" : "décousu");
    }

    /** Change le mode d'un pli (ciseaux) et resserre / desserre les coutures. */
    public static void setMode(Fold f, int mode) {
        f.mode = mode;
        if (!f.temporary()) save();
        Markers.refresh(f);
    }

    /** Force le chargement du tronçon (chunk) d'un bout ; faux s'il l'était déjà (par /forceload ou un autre pli). */
    private static boolean force(ServerLevel level, BlockPos pos) {
        if (level == null) return false;
        String dim = Compat.dim(level);
        for (Fold o : ALL) {
            if ((o.forcedA && sameChunk(o.a, dim, pos)) || (o.forcedB && sameChunk(o.b, dim, pos))) return false;
        }
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (Compat.isChunkForced(level, cx, cz)) return false;
        level.setChunkForced(cx, cz, true);
        return true;
    }

    /** Libère le tronçon d'un bout, sauf si un autre pli y a un bout (il reprend alors le forçage). */
    private static void unforce(Anchor end, boolean forced) {
        if (!forced) return;
        for (Fold o : ALL) {
            if (sameChunk(o.a, end.dim(), end.pos())) { o.forcedA = true; return; }
            if (sameChunk(o.b, end.dim(), end.pos())) { o.forcedB = true; return; }
        }
        ServerLevel level = level(end);
        if (level != null) level.setChunkForced(end.pos().getX() >> 4, end.pos().getZ() >> 4, false);
    }

    private static boolean sameChunk(Anchor a, String dim, BlockPos q) {
        BlockPos p = a.pos();
        return a.dim().equals(dim) && (p.getX() >> 4) == (q.getX() >> 4) && (p.getZ() >> 4) == (q.getZ() >> 4);
    }

    private static boolean poweredNow(Fold f) {
        return powered(f.a) || powered(f.b);
    }

    private static boolean powered(Anchor a) {
        ServerLevel level = level(a);
        return level != null && level.isLoaded(a.pos()) && level.hasNeighborSignal(a.pos());
    }

    // ───────────────────────── entretien ─────────────────────────

    /** Appelé à chaque tick serveur, une fois pour tous les mondes, avant {@link #tick(ServerLevel)}. */
    public static void tickGlobal() {
        if (server == null || ALL.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Fold f : new ArrayList<>(ALL)) {
            if (f.temporary() && now >= f.expires) {
                remove(f, false);
                continue;
            }
            boolean p = poweredNow(f);
            if (p != f.powered) {
                f.powered = p;
                Markers.refresh(f);
                ServerLevel la = level(f.a), lb = level(f.b);
                if (la != null) Effects.tighten(la, f.a, f.color, p);
                if (lb != null) Effects.tighten(lb, f.b, f.color, p);
            }
        }
        if (now % 20 == 0) {
            for (Fold f : new ArrayList<>(ALL)) {
                if (!intact(f.a) || !intact(f.b)) remove(f, true);
            }
        }
    }

    /** Appelé à chaque tick serveur, pour chaque dimension. */
    public static void tick(ServerLevel level) {
        if (ALL.isEmpty() && PENDING.isEmpty()) return;
        long time = level.getGameTime();
        List<Fold> here = in(level);
        if (!here.isEmpty()) {
            String dim = Compat.dim(level);
            if (time % 20 == 10) for (Fold f : here) Markers.heal(level, f);
            if (time % 30 == 0) for (Fold f : here) Effects.idle(level, f, dim);
            Transit.tick(level, here, time);
        }
        if (time % 10 == 0 && !PENDING.isEmpty()) {
            String dim = Compat.dim(level);
            PENDING.values().removeIf(p -> time - p.time() > 20 * 300 || p.time() > time + 20 * 300);
            for (Pending p : PENDING.values()) if (p.anchor().dim().equals(dim)) Effects.pending(level, p.anchor());
        }
    }

    static void playAt(ServerLevel level, Anchor a, net.minecraft.sounds.SoundEvent sound, float vol, float pitch) {
        var c = a.center();
        level.playSound(null, c.x, c.y, c.z, sound, SoundSource.BLOCKS, vol, pitch);
    }
}
