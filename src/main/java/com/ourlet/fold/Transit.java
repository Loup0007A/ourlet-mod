package com.ourlet.fold;

import com.ourlet.OurletConfig;
import com.ourlet.platform.Compat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Le transfert : tout ce qui touche une face cousue ressort par l'autre, avec sa vitesse
 * (tournée comme la face, puis tendue ou amortie selon le fil) et son regard.
 *
 * Chaque tick, pour chaque bout, on regarde les entités juste devant la face :
 * celles qui vont la toucher pendant ce tick (vitesse dirigée vers la face) ou qui viennent
 * de s'y poser. Les projectiles rapides sont interceptés avant l'impact grâce à la vitesse.
 */
public final class Transit {

    private Transit() {}

    /** Distance devant la face dans laquelle on cherche des entités (les flèches vont vite). */
    private static final double REACH = 5.0;
    /** Une entité qui vient de ressortir ne peut pas rentrer par la face de sortie avant ce délai. */
    private static final int COOLDOWN = 8;

    /** Clé (entité, pli, bout) → tick jusqu'auquel ce bout lui est fermé. */
    private static final Map<Long, Long> COOLDOWNS = new HashMap<>();
    /** Dernier tick où une entité touchait un bout (clé : entité + pli + bout). */
    private static final Map<Long, Long> CONTACT = new HashMap<>();
    /**
     * Mouvement des joueurs : le serveur ne connaît pas leur vitesse, on la déduit de leurs dernières
     * positions (la plus récente en tête). On fait la moyenne sur {@link #WINDOW} ticks : un serveur en
     * retard reçoit les pas du client par paquets, et un seul tick pourrait compter trois pas d'un coup.
     */
    private static final Map<UUID, Deque<Vec3>> TRACK = new HashMap<>();
    private static final Map<UUID, Vec3> PLAYER_VEL = new HashMap<>();
    private static final int WINDOW = 3;
    /** Les derniers passages de chaque joueur (pour « Boucle infinie »). */
    private static final Map<UUID, Deque<Long>> RECENT = new HashMap<>();
    private static final Map<UUID, Long> LAST_TITLE = new HashMap<>();

    /** Le ruban de fil qui suit une entité après son passage. */
    private record Trail(Entity entity, int color, int[] left) {}

    private static final List<Trail> TRAILS = new ArrayList<>();
    private static int passages;

    /** Pour le test automatique : où et à quelle vitesse chaque entité est ressortie (rempli seulement en test). */
    public record Exit(Vec3 pos, Vec3 velocity) {}

    public static final Map<Integer, Exit> TEST_EXITS = new HashMap<>();
    /** Pour le test : nombre de passages de chaque entité. */
    public static final Map<Integer, Integer> TEST_COUNTS = new HashMap<>();

    public static void reset() {
        COOLDOWNS.clear();
        CONTACT.clear();
        TRACK.clear();
        PLAYER_VEL.clear();
        RECENT.clear();
        LAST_TITLE.clear();
        TRAILS.clear();
    }

    /** Nombre total de passages depuis le démarrage (pour le test automatique). */
    public static int passages() {
        return passages;
    }

    /** À appeler une fois par tick et par dimension, avant {@link #tick}. */
    public static void trackPlayers(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            Vec3 now = p.position();
            Deque<Vec3> seen = TRACK.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
            Vec3 last = seen.peekFirst();
            if (last != null && now.distanceToSqr(last) > 16) seen.clear(); // téléporté autrement
            seen.addFirst(now);
            while (seen.size() > WINDOW + 1) seen.removeLast();
            PLAYER_VEL.put(p.getUUID(), seen.size() < 2 ? Vec3.ZERO : now.subtract(seen.peekLast()).scale(1.0 / (seen.size() - 1)));
        }
    }

    public static Vec3 velocity(Entity e) {
        if (e instanceof ServerPlayer p) return PLAYER_VEL.getOrDefault(p.getUUID(), Vec3.ZERO);
        return e.getDeltaMovement();
    }

    private static long key(Entity e, Fold f, Anchor end) {
        return ((long) e.getId() << 21) ^ ((long) f.id << 1) ^ (end == f.a ? 0 : 1);
    }

    static void tick(ServerLevel level, List<Fold> folds, long time) {
        if (time % 200 == 0) {
            CONTACT.values().removeIf(t -> time - t > 40);
            COOLDOWNS.values().removeIf(t -> t < time);
        }
        for (Fold f : folds) {
            end(level, f, f.a, time);
            end(level, f, f.b, time);
        }
    }

    /** Les rubans de fil derrière les entités qui viennent de passer. */
    static void trails(ServerLevel level) {
        if (TRAILS.isEmpty()) return;
        for (Iterator<Trail> it = TRAILS.iterator(); it.hasNext(); ) {
            Trail t = it.next();
            Entity e = t.entity;
            if (!e.isAlive() || t.left[0]-- <= 0) {
                it.remove();
                continue;
            }
            if (e.level() != level) continue;
            AABB bb = e.getBoundingBox();
            Effects.trail(level, new Vec3((bb.minX + bb.maxX) / 2, bb.minY + bb.getYsize() * 0.4, (bb.minZ + bb.maxZ) / 2), t.color);
        }
    }

    private static boolean eligible(Entity e) {
        return e.isAlive() && !e.isSpectator() && !e.isPassenger() && !e.isVehicle()
            && !(e instanceof Display) && !(e instanceof HangingEntity) && !(e instanceof Marker)
            && !(e instanceof AreaEffectCloud) && !(e instanceof LightningBolt)
            && !OurletConfig.blacklist.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
    }

    private static double half(AABB bb, Direction.Axis axis) {
        return switch (axis) {
            case X -> bb.getXsize() / 2;
            case Y -> bb.getYsize() / 2;
            case Z -> bb.getZsize() / 2;
        };
    }

    private static void end(ServerLevel level, Fold f, Anchor here, long time) {
        if (!here.dim().equals(Compat.dim(level)) || !f.canEnter(here)) return;
        if (!level.isLoaded(here.pos())) return;
        Direction face = here.face();
        double h = f.half();
        AABB block = new AABB(here.pos()).inflate(face.getAxis() == Direction.Axis.X ? 0 : h - 0.5,
            face.getAxis() == Direction.Axis.Y ? 0 : h - 0.5, face.getAxis() == Direction.Axis.Z ? 0 : h - 0.5);
        AABB search = block.expandTowards(face.getStepX() * REACH, face.getStepY() * REACH, face.getStepZ() * REACH)
            .inflate(face.getAxis() == Direction.Axis.X ? 0 : 1.0, face.getAxis() == Direction.Axis.Y ? 0 : 1.5,
                face.getAxis() == Direction.Axis.Z ? 0 : 1.0);
        List<Entity> list = level.getEntities((Entity) null, search, Transit::eligible);
        if (list.isEmpty()) return;
        Vec3 n = here.normal(), u = here.upVec(), r = here.rightVec(), c0 = here.center();
        Direction.Axis na = face.getAxis(), ua = here.up().getAxis(), ra = here.right().getAxis();
        for (Entity e : list) {
            AABB bb = e.getBoundingBox();
            Vec3 c = bb.getCenter();
            double s = c.subtract(c0).dot(n) - half(bb, na);
            if (s < -0.4) continue;
            Vec3 v = velocity(e);
            double vn = v.dot(n);
            long key = key(e, f, here);
            Long lastTouch = CONTACT.get(key);
            boolean touchedBefore = lastTouch != null && lastTouch >= time - 1;

            double t;
            if (vn < -1e-4 && s + vn <= 0.05) t = Math.max(0, Math.min(1, s / -vn));
            else if (s <= 0.03 && !touchedBefore && !(e instanceof Projectile)) t = 0;
            else t = -1;

            Vec3 ci = t > 0 ? c.add(v.scale(t)) : c;
            Vec3 d = ci.subtract(c0);
            boolean over = Math.abs(d.dot(u)) < h + half(bb, ua) - 0.1 && Math.abs(d.dot(r)) < h + half(bb, ra) - 0.1;
            if (!over) continue;
            if (s <= 0.05) CONTACT.put(key, time);
            if (t < 0) continue;
            Long cd = COOLDOWNS.get(key);
            if (cd != null && cd >= time) continue;
            pass(level, f, here, e, ci, v, time);
        }
    }

    /** La vitesse de sortie, selon la tension du fil. */
    static Vec3 tensioned(Fold f, Anchor there, Vec3 v2) {
        return switch (f.tension()) {
            case Fold.ELASTIC -> {
                Vec3 out = v2.scale(1.5).add(there.normal().scale(0.35));
                yield out.length() > 6 ? out.normalize().scale(6) : out;
            }
            case Fold.DAMPED -> v2.scale(0.15);
            default -> v2;
        };
    }

    /** Fait passer {@code e} par le pli. Renvoie faux si la sortie est bouchée. */
    public static boolean pass(ServerLevel level, Fold f, Anchor here, Entity e, Vec3 impactCenter, Vec3 v, long time) {
        Anchor there = f.other(here);
        ServerLevel to = Folds.level(there);
        if (to == null) return false;
        AABB bb = e.getBoundingBox();
        double hx = bb.getXsize() / 2, hy = bb.getYsize() / 2, hz = bb.getZsize() / 2;
        Vec3 n2 = there.normal(), u2 = there.upVec(), r2 = there.rightVec();
        double out = half(bb, there.face().getAxis()) + 0.03;
        Vec3 base = f.mapPoint(here, impactCenter).add(n2.scale(out));

        Vec3 spot = null;
        double[] steps = {0, -0.25, 0.25, -0.5, 0.5, -0.75, 0.75, -1.0, 1.0, -1.25, 1.25};
        double[] side = {0, -0.3, 0.3};
        search:
        for (double du : steps) {
            for (double dr : side) {
                Vec3 cand = base.add(u2.scale(du)).add(r2.scale(dr));
                AABB box = new AABB(cand.x - hx, cand.y - hy, cand.z - hz, cand.x + hx, cand.y + hy, cand.z + hz);
                if (to.noCollision(e, box)) {
                    spot = cand;
                    break search;
                }
            }
        }
        if (spot == null) {
            COOLDOWNS.put(key(e, f, here), time + COOLDOWN);
            Effects.snag(level, f, here);
            return false;
        }

        Vec3 v2 = tensioned(f, there, Fold.mapVector(here, there, v));
        Vec3 look = Fold.mapVector(here, there, e.getLookAngle());
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -look.y))));
        if (Math.abs(look.y) > 0.999) yaw = e.getYRot();
        double x = spot.x, y = spot.y - hy, z = spot.z;

        // l'entité ne doit pas retraverser aussitôt la face de sortie (mais elle peut rentrer par l'autre : chute sans fin)
        CONTACT.put(key(e, f, there), time);
        COOLDOWNS.put(key(e, f, there), time + COOLDOWN);

        e.resetFallDistance();
        Entity moved = e;
        if (e instanceof ServerPlayer p) {
            Compat.teleportPlayer(p, to, x, y, z, yaw, pitch);
            Deque<Vec3> seen = new ArrayDeque<>();
            seen.add(new Vec3(x, y, z));
            TRACK.put(p.getUUID(), seen);
            PLAYER_VEL.put(p.getUUID(), v2);
            p.setDeltaMovement(v2);
            Compat.syncMotion(p);
            arrived(p, f, there, v2, time);
        } else if (to != level) {
            // autre dimension : l'entité est recréée dans l'autre monde, avec sa vitesse
            moved = Compat.moveAcross(e, to, new Vec3(x, y, z), v2, yaw, pitch);
            if (moved == null) return false;
            CONTACT.put(key(moved, f, there), time);
            COOLDOWNS.put(key(moved, f, there), time + COOLDOWN);
        } else {
            e.teleportTo(x, y, z);
            e.setYRot(yaw);
            e.setXRot(pitch);
            e.setYHeadRot(yaw);
            e.yRotO = yaw;
            e.xRotO = pitch;
            // sinon les projectiles « balaient » depuis leur ancienne position et heurtent la face d'entrée
            e.setOldPosAndRot();
            e.setDeltaMovement(v2);
            Compat.syncMotion(e);
        }
        passages++;
        TRAILS.add(new Trail(moved, f.color(), new int[]{14}));
        if (com.ourlet.debug.ServerSelfTest.enabled()) {
            TEST_EXITS.put(e.getId(), new Exit(new Vec3(x, y, z), v2));
            TEST_EXITS.put(moved.getId(), new Exit(new Vec3(x, y, z), v2));
            TEST_COUNTS.merge(e.getId(), 1, Integer::sum);
            if (moved != e) TEST_COUNTS.merge(moved.getId(), 1, Integer::sum);
        }
        Effects.transit(f, here, there, bb.getSize(), v.length());
        return true;
    }

    /** Un joueur vient de ressortir : progrès, et un titre discret quand le voyage est long. */
    private static void arrived(ServerPlayer p, Fold f, Anchor there, Vec3 v2, long time) {
        Progress.grant(p, "pass");
        if (v2.length() > 1.0) Progress.grant(p, "fling");
        Deque<Long> recent = RECENT.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
        recent.addLast(time);
        while (!recent.isEmpty() && recent.peekFirst() < time - 200) recent.pollFirst();
        if (recent.size() >= 10) Progress.grant(p, "loop");

        boolean far = f.crossDimension() || f.span() >= 300;
        if (!far && f.name() == null) return;
        Long last = LAST_TITLE.get(p.getUUID());
        if (last != null && time - last < 60) return;
        LAST_TITLE.put(p.getUUID(), time);
        int rgb = Effects.rgb(f.color());
        MutableComponent sub = Component.empty();
        if (f.name() != null) sub.append(Component.literal("« " + f.name() + " »  ").withStyle(s -> s.withColor(rgb)));
        sub.append(Component.translatable(Gaze.dimName(there.dim())).withStyle(ChatFormatting.WHITE));
        sub.append(Component.literal("  " + there.pos().getX() + " " + there.pos().getY() + " " + there.pos().getZ())
            .withStyle(ChatFormatting.GRAY));
        Compat.title(p, Component.literal("⇄").withStyle(s -> s.withColor(rgb)), sub, 4, 26, 12);
    }
}
