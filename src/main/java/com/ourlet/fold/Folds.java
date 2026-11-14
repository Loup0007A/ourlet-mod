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
 */
public final class Folds {

    private Folds() {}

    /** Plis par joueur. */
    public static final int MAX_PER_PLAYER = 12;

    /** Un bout de pli vu depuis une face : {@code here} est la face touchée, {@code there} l'autre. */
    public record End(Fold fold, Anchor here) {
        public Anchor there() {
            return fold.other(here);
        }
    }

    /** Premier point posé avec l'aiguille, en attente du second. */
    public record Pending(String dimension, Anchor anchor, long time) {}

    private static final List<Fold> ALL = new ArrayList<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static MinecraftServer server;
    private static Path file;
    private static int nextId = 1;

    // ───────────────────────── cycle de vie ─────────────────────────

    public static void load(MinecraftServer s) {
        server = s;
        ALL.clear();
        PENDING.clear();
        nextId = 1;
        file = s.getWorldPath(LevelResource.ROOT).resolve("data").resolve("ourlet_folds.txt");
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    Fold f = Fold.parse(line);
                    ALL.add(f);
                    nextId = Math.max(nextId, f.id + 1);
                } catch (RuntimeException e) {
                    Ourlet.LOGGER.warn("Pli illisible ignoré : {}", line);
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
        lines.add("# Ourlet : un pli par ligne (id;propriétaire;dimension;A;B;couleur;forçageA;forçageB)");
        for (Fold f : ALL) lines.add(f.serialize());
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
        save();
        ALL.clear();
        PENDING.clear();
        server = null;
        file = null;
        Transit.reset();
        Relay.reset();
    }

    // ───────────────────────── recherche ─────────────────────────

    public static List<Fold> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<Fold> in(Level level) {
        String dim = Compat.dim(level);
        List<Fold> out = new ArrayList<>();
        for (Fold f : ALL) if (f.dimension.equals(dim)) out.add(f);
        return out;
    }

    public static End find(Level level, BlockPos pos, Direction face) {
        if (ALL.isEmpty()) return null;
        String dim = Compat.dim(level);
        for (Fold f : ALL) {
            if (!f.dimension.equals(dim)) continue;
            if (f.a.is(pos, face)) return new End(f, f.a);
            if (f.b.is(pos, face)) return new End(f, f.b);
        }
        return null;
    }

    public static Fold byId(int id) {
        for (Fold f : ALL) if (f.id == id) return f;
        return null;
    }

    public static int owned(UUID owner) {
        int n = 0;
        for (Fold f : ALL) if (f.owner.equals(owner)) n++;
        return n;
    }

    public static Pending pending(UUID player) {
        return PENDING.get(player);
    }

    public static void setPending(UUID player, Pending p) {
        if (p == null) PENDING.remove(player);
        else PENDING.put(player, p);
    }

    public static Map<UUID, Pending> pendingAll() {
        return PENDING;
    }

    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    // ───────────────────────── couture / découture ─────────────────────────

    public static Fold create(ServerLevel level, UUID owner, Anchor a, Anchor b) {
        int id = nextId++;
        Fold f = new Fold(id, owner, Compat.dim(level), a, b, Math.floorMod(id - 1, 4));
        f.forcedA = force(level, a.pos(), true);
        f.forcedB = force(level, b.pos(), true);
        ALL.add(f);
        save();
        Markers.place(level, f);
        Effects.stitch(level, f.a, f.color);
        Effects.stitch(level, f.b, f.color);
        Ourlet.LOGGER.info("Pli #{} cousu : {} {} <-> {} {} ({} blocs)", f.id, a.pos().toShortString(), a.face(),
            b.pos().toShortString(), b.face(), f.span());
        return f;
    }

    public static void remove(ServerLevel level, Fold f, boolean torn) {
        if (!ALL.remove(f)) return;
        Markers.remove(level, f);
        if (f.forcedA && !otherUses(f, f.a.pos())) level.setChunkForced(f.a.pos().getX() >> 4, f.a.pos().getZ() >> 4, false);
        if (f.forcedB && !otherUses(f, f.b.pos())) level.setChunkForced(f.b.pos().getX() >> 4, f.b.pos().getZ() >> 4, false);
        Effects.unstitch(level, f.a, f.color, torn);
        Effects.unstitch(level, f.b, f.color, torn);
        save();
        Ourlet.LOGGER.info("Pli #{} {}", f.id, torn ? "déchiré (bloc disparu)" : "décousu");
    }

    /** Force le chargement du tronçon (chunk) d'un bout ; faux s'il l'était déjà (par /forceload ou un autre pli). */
    private static boolean force(ServerLevel level, BlockPos pos, boolean on) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (Fold o : ALL) {
            if ((o.forcedA && sameChunk(o.a.pos(), pos)) || (o.forcedB && sameChunk(o.b.pos(), pos))) return false;
        }
        if (Compat.isChunkForced(level, cx, cz)) return false;
        level.setChunkForced(cx, cz, on);
        return true;
    }

    private static boolean otherUses(Fold self, BlockPos pos) {
        for (Fold o : ALL) {
            if (o == self) continue;
            if (sameChunk(o.a.pos(), pos) || sameChunk(o.b.pos(), pos)) {
                // l'autre pli reprend le forçage à son compte
                if (sameChunk(o.a.pos(), pos)) o.forcedA = true;
                else o.forcedB = true;
                return true;
            }
        }
        return false;
    }

    private static boolean sameChunk(BlockPos p, BlockPos q) {
        return (p.getX() >> 4) == (q.getX() >> 4) && (p.getZ() >> 4) == (q.getZ() >> 4);
    }

    // ───────────────────────── entretien ─────────────────────────

    /** Appelé à chaque tick serveur, pour chaque dimension. */
    public static void tick(ServerLevel level) {
        if (ALL.isEmpty() && PENDING.isEmpty()) return;
        long time = level.getGameTime();
        List<Fold> here = in(level);
        if (!here.isEmpty()) {
            if (time % 20 == 0) {
                for (Fold f : here) {
                    if (!intact(level, f.a) || !intact(level, f.b)) remove(level, f, true);
                }
                here = in(level);
            }
            if (time % 100 == 0) for (Fold f : here) Markers.heal(level, f);
            if (time % 30 == 0) for (Fold f : here) Effects.idle(level, f);
            Transit.tick(level, here, time);
        }
        if (time % 10 == 0 && !PENDING.isEmpty()) {
            String dim = Compat.dim(level);
            PENDING.values().removeIf(p -> time - p.time() > 20 * 120 || p.time() > time);
            for (Pending p : PENDING.values()) if (p.dimension().equals(dim)) Effects.pending(level, p.anchor());
        }
    }

    private static boolean intact(ServerLevel level, Anchor a) {
        if (!level.isLoaded(a.pos())) return true;
        BlockState s = level.getBlockState(a.pos());
        return !s.isAir() && blockId(s).equals(a.block());
    }

    public static MinecraftServer server() {
        return server;
    }

    static void playAt(ServerLevel level, Anchor a, net.minecraft.sounds.SoundEvent sound, float vol, float pitch) {
        var c = a.center();
        level.playSound(null, c.x, c.y, c.z, sound, SoundSource.BLOCKS, vol, pitch);
    }
}
