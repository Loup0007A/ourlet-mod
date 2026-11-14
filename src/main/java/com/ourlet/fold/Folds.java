package com.ourlet.fold;

import com.ourlet.Ourlet;
import com.ourlet.OurletConfig;
import com.ourlet.platform.Compat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /** Durée d'un pli de poche (dé à coudre), en ticks (réglage pocket_seconds). */
    public static int pocketTicks() {
        return OurletConfig.pocketSeconds * 20;
    }

    /** Ce joueur peut-il découdre, couper, teindre ou nommer ce pli ? (propriétaire, opérateur, ou protection désactivée) */
    public static boolean mayEdit(ServerPlayer p, Fold f) {
        return !OurletConfig.protect || f.owner.equals(p.getUUID()) || Compat.isOp(p);
    }

    /** Le nom du propriétaire s'il est connecté, sinon « un autre joueur ». */
    public static Component ownerName(Fold f) {
        var online = server == null ? null : server.getPlayerList().getPlayer(f.owner);
        return online != null ? online.getName() : Component.translatable("ourlet.someone");
    }

    /** Un bout de pli vu depuis une face : {@code here} est la face touchée, {@code there} l'autre. */
    public record End(Fold fold, Anchor here) {
        public Anchor there() {
            return fold.other(here);
        }

        public boolean open() {
            return fold.canEnter(here);
        }
    }

    /** Premier point posé avec l'aiguille, en attente du second ({@code size} : 1, ou 3 avec la bobine). */
    public record Pending(Anchor anchor, long time, int size) {}

    private static final List<Fold> ALL = new ArrayList<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** La « maison » de chaque joueur, pour le dé à coudre. */
    private static final Map<UUID, Anchor> HOMES = new HashMap<>();
    /** Les tronçons (chunks) qu'Ourlet garde chargés : "dimension|x|z". */
    private static final Set<String> FORCED = new HashSet<>();
    private static boolean forcingDirty;
    /** Petites tâches à faire dans quelques ticks (animations). */
    private static final List<long[]> LATER_AT = new ArrayList<>();
    private static final List<Runnable> LATER = new ArrayList<>();
    private static MinecraftServer server;
    private static Path file;
    private static int nextId = 1;

    // ───────────────────────── cycle de vie ─────────────────────────

    public static void load(MinecraftServer s) {
        server = s;
        ALL.clear();
        PENDING.clear();
        HOMES.clear();
        FORCED.clear();
        LATER.clear();
        LATER_AT.clear();
        nextId = 1;
        forcingDirty = true;
        file = s.getWorldPath(LevelResource.ROOT).resolve("data").resolve("ourlet_folds.txt");
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    String[] p = line.split(";");
                    if (line.startsWith("H;")) {
                        HOMES.put(UUID.fromString(p[1]), Anchor.parse(p[2], "minecraft:overworld"));
                        continue;
                    }
                    if (line.startsWith("C;")) {
                        FORCED.add(p[1] + "|" + p[2] + "|" + p[3]);
                        continue;
                    }
                    Fold f = Fold.parse(line, a -> FORCED.add(chunkKey(a.dim(), a.pos())));
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
        lines.add("# Ourlet : id;propriétaire;dim;A;B;couleur;-;-;mode;taille;tension;nom — H;joueur;maison — C;dim;x;z (tronçon chargé)");
        for (Fold f : ALL) if (!f.temporary()) lines.add(f.serialize());
        HOMES.forEach((uuid, a) -> lines.add("H;" + uuid + ";" + a.serialize()));
        for (String c : FORCED) {
            String[] p = c.split("\\|");
            lines.add("C;" + p[0] + ";" + p[1] + ";" + p[2]);
        }
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
        // les plis de poche ne survivent pas à l'arrêt : on les défait proprement (coutures)
        for (Fold f : new ArrayList<>(ALL)) if (f.temporary()) remove(f, false);
        refreshForcing();
        save();
        ALL.clear();
        PENDING.clear();
        HOMES.clear();
        FORCED.clear();
        LATER.clear();
        LATER_AT.clear();
        server = null;
        file = null;
        Transit.reset();
        Relay.reset();
        Gaze.reset();
        Progress.reset();
        Hoppers.reset();
    }

    // ───────────────────────── recherche ─────────────────────────

    public static MinecraftServer server() {
        return server;
    }

    public static long now() {
        return server == null ? 0 : server.overworld().getGameTime();
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

    /** Le bout de pli qui couvre cette face de bloc (une grande couture couvre 3×3 faces), ou null. */
    public static End find(Level level, BlockPos pos, Direction face) {
        if (ALL.isEmpty()) return null;
        String dim = Compat.dim(level);
        for (Fold f : ALL) {
            if (f.a.covers(dim, pos, face, f.size)) return new End(f, f.a);
            if (f.b.covers(dim, pos, face, f.size)) return new End(f, f.b);
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

    /** Une face de bloc qui peut recevoir un point : pleine, solide. */
    public static boolean sewable(Level level, BlockPos pos, Direction face) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.isFaceSturdy(level, pos, face);
    }

    /** Toute une couture (1 ou 3×3 faces) est-elle cousable, et libre ? */
    public static boolean sewable(Level level, Anchor a, int size) {
        for (BlockPos c : a.cells(size)) {
            if (!sewable(level, c, a.face()) || find(level, c, a.face()) != null) return false;
        }
        return true;
    }

    /** Le bloc d'un bout est-il toujours là ? (vrai si son tronçon n'est pas chargé) */
    public static boolean intact(Anchor a) {
        return intact(a, 1);
    }

    public static boolean intact(Anchor a, int size) {
        ServerLevel level = level(a);
        if (level == null) return false;
        if (!level.isLoaded(a.pos())) return true;
        BlockState s = level.getBlockState(a.pos());
        if (s.isAir() || !blockId(s).equals(a.block())) return false;
        if (size > 1) {
            for (BlockPos c : a.cells(size)) if (level.isLoaded(c) && level.getBlockState(c).isAir()) return false;
        }
        return true;
    }

    // ───────────────────────── couture / découture ─────────────────────────

    public static Fold create(UUID owner, Anchor a, Anchor b) {
        return create(owner, a, b, -1, 1);
    }

    public static Fold create(UUID owner, Anchor a, Anchor b, int lifetime) {
        return create(owner, a, b, lifetime, 1);
    }

    /** {@code lifetime} > 0 : pli de poche, défait au bout de ce nombre de ticks ; {@code size} : 1 ou 3. */
    public static Fold create(UUID owner, Anchor a, Anchor b, int lifetime, int size) {
        int id = nextId++;
        Fold f = new Fold(id, owner, a, b, lifetime > 0 ? Fold.BASTING : Math.floorMod(id - 1, 4), size == 3 ? 3 : 1);
        if (lifetime > 0) f.expires = now() + lifetime;
        ALL.add(f);
        refreshForcing();
        if (!f.temporary()) save();
        f.powered = poweredNow(f);
        Markers.place(f);
        Effects.sewingAnimation(f);
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            if (f.temporary()) Progress.grant(player, "pocket");
            else {
                Progress.grant(player, "sew");
                if (f.size > 1) Progress.grant(player, "patchwork");
                if (f.crossDimension()) Progress.grant(player, "dimensions");
                else if (f.span() >= 1000) Progress.grant(player, "far");
            }
        }
        Ourlet.LOGGER.info("Pli #{} cousu : {} {} {} <-> {} {} {}{}{}", f.id, a.dim(), a.pos().toShortString(), a.face(),
            b.dim(), b.pos().toShortString(), b.face(), f.size > 1 ? " (3×3)" : "", f.temporary() ? " (de poche)" : "");
        return f;
    }

    public static void remove(Fold f, boolean torn) {
        if (!ALL.remove(f)) return;
        Markers.remove(f);
        refreshForcing();
        ServerLevel la = level(f.a), lb = level(f.b);
        if (la != null) Effects.unstitch(la, f, f.a, torn);
        if (lb != null) Effects.unstitch(lb, f, f.b, torn);
        if (!f.temporary()) save();
        Ourlet.LOGGER.info("Pli #{} {}", f.id, torn ? "déchiré (bloc disparu)" : "décousu");
    }

    /** Change le mode d'un pli (ciseaux) et resserre / desserre les coutures. */
    public static void setMode(Fold f, int mode) {
        f.mode = mode;
        if (!f.temporary()) save();
        Markers.refresh(f);
    }

    /** Teint le fil (les deux coutures changent de couleur). */
    public static void recolor(Fold f, int color) {
        f.color = color;
        if (!f.temporary()) save();
        Markers.remove(f);
        Markers.place(f);
    }

    public static void rename(Fold f, String name) {
        f.name = name == null || name.isBlank() ? null : name.length() > 40 ? name.substring(0, 40) : name;
        if (!f.temporary()) save();
    }

    public static void setTension(Fold f, int tension) {
        f.tension = tension;
        if (!f.temporary()) save();
    }

    // ───────────────────────── tronçons chargés ─────────────────────────

    private static String chunkKey(String dim, BlockPos p) {
        return dim + "|" + (p.getX() >> 4) + "|" + (p.getZ() >> 4);
    }

    /**
     * Garde chargés tous les tronçons qui contiennent un bloc cousu, et seulement ceux-là.
     * Un tronçon déjà forcé par quelqu'un d'autre (/forceload) n'est ni pris ni relâché.
     */
    private static void refreshForcing() {
        if (server == null) return;
        Set<String> wanted = new HashSet<>();
        if (OurletConfig.forceLoad) {
            for (Fold f : ALL) {
                for (Anchor end : new Anchor[]{f.a, f.b}) {
                    for (BlockPos c : end.cells(f.size)) wanted.add(chunkKey(end.dim(), c));
                }
            }
        }
        boolean changed = false;
        for (String key : wanted) {
            if (FORCED.contains(key)) continue;
            String[] p = key.split("\\|");
            ServerLevel level = level(p[0]);
            if (level == null) continue;
            int cx = Integer.parseInt(p[1]), cz = Integer.parseInt(p[2]);
            if (Compat.isChunkForced(level, cx, cz)) continue;
            level.setChunkForced(cx, cz, true);
            FORCED.add(key);
            changed = true;
        }
        for (String key : new ArrayList<>(FORCED)) {
            if (wanted.contains(key)) continue;
            String[] p = key.split("\\|");
            ServerLevel level = level(p[0]);
            if (level != null) level.setChunkForced(Integer.parseInt(p[1]), Integer.parseInt(p[2]), false);
            FORCED.remove(key);
            changed = true;
        }
        forcingDirty = false;
        if (changed) save();
    }

    public static int forcedChunks() {
        return FORCED.size();
    }

    // ───────────────────────── redstone ─────────────────────────

    private static boolean poweredNow(Fold f) {
        return powered(f.a, f.size) || powered(f.b, f.size);
    }

    private static boolean powered(Anchor a, int size) {
        ServerLevel level = level(a);
        if (level == null || !level.isLoaded(a.pos())) return false;
        if (level.hasNeighborSignal(a.pos())) return true;
        if (size > 1) for (BlockPos c : a.cells(size)) if (level.isLoaded(c) && level.hasNeighborSignal(c)) return true;
        return false;
    }

    // ───────────────────────── entretien ─────────────────────────

    /** Lance {@code r} dans {@code ticks} ticks (petites animations). */
    public static void later(int ticks, Runnable r) {
        LATER.add(r);
        LATER_AT.add(new long[]{now() + Math.max(1, ticks)});
    }

    /** Appelé à chaque tick serveur, une fois pour tous les mondes, avant {@link #tick(ServerLevel)}. */
    public static void tickGlobal() {
        if (server == null) return;
        long now = now();
        if (!LATER.isEmpty()) {
            List<Runnable> due = new ArrayList<>();
            for (int i = LATER.size() - 1; i >= 0; i--) {
                if (LATER_AT.get(i)[0] <= now) {
                    due.add(0, LATER.remove(i));
                    LATER_AT.remove(i);
                }
            }
            for (Runnable r : due) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    Ourlet.LOGGER.warn("Animation interrompue", e);
                }
            }
        }
        if (forcingDirty) refreshForcing();
        if (ALL.isEmpty()) return;
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
                if (la != null) Effects.tighten(la, f, f.a, p);
                if (lb != null) Effects.tighten(lb, f, f.b, p);
            }
        }
        if (now % 20 == 0) {
            for (Fold f : new ArrayList<>(ALL)) {
                if (!intact(f.a, f.size) || !intact(f.b, f.size)) remove(f, true);
            }
        }
    }

    /** Appelé à chaque tick serveur, pour chaque dimension. */
    public static void tick(ServerLevel level) {
        long time = level.getGameTime();
        if (!ALL.isEmpty()) {
            List<Fold> here = in(level);
            if (!here.isEmpty()) {
                String dim = Compat.dim(level);
                if (time % 20 == 10) for (Fold f : here) Markers.heal(level, f);
                if (time % 10 == 0) for (Fold f : here) Effects.idle(level, f, dim, time);
                Transit.tick(level, here, time);
                Hoppers.tick(level, here, time);
            }
        }
        Transit.trails(level);
        if (time % 10 == 0) {
            String dim = Compat.dim(level);
            if (!PENDING.isEmpty()) {
                PENDING.values().removeIf(p -> time - p.time() > 20 * 300 || p.time() > time + 20 * 300);
                for (Pending p : PENDING.values()) if (p.anchor().dim().equals(dim)) Effects.pending(level, p.anchor(), p.size());
            }
            Ariadne.tick(level);
        }
    }

    static void playAt(ServerLevel level, Anchor a, net.minecraft.sounds.SoundEvent sound, float vol, float pitch) {
        var c = a.center();
        level.playSound(null, c.x, c.y, c.z, sound, SoundSource.BLOCKS, vol, pitch);
    }
}
