package com.ourlet.fold;

import com.ourlet.platform.Compat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
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
 * (tournée comme la face) et son regard.
 *
 * Chaque tick, pour chaque bout, on regarde les entités juste devant la face :
 * celles qui vont la toucher pendant ce tick (vitesse dirigée vers la face) ou qui viennent
 * de s'y poser. Les projectiles rapides sont interceptés avant l'impact grâce à la vitesse.
 */
public final class Transit {

    private Transit() {}

    /** Distance devant la face dans laquelle on cherche des entités (les flèches vont vite). */
    private static final double REACH = 5.0;
    private static final int COOLDOWN = 8;

    private static final Map<Integer, Long> COOLDOWNS = new HashMap<>();
    /** Dernier tick où une entité touchait un bout (clé : entité + pli + bout). */
    private static final Map<Long, Long> CONTACT = new HashMap<>();
    /** Mouvement des joueurs (le serveur ne connaît pas leur vitesse : on la déduit des positions). */
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Vec3> PLAYER_VEL = new HashMap<>();
    private static int passages;

    /** Pour le test automatique : où et à quelle vitesse chaque entité est ressortie (rempli seulement en test). */
    public record Exit(Vec3 pos, Vec3 velocity) {}

    public static final Map<Integer, Exit> TEST_EXITS = new HashMap<>();

    public static void reset() {
        COOLDOWNS.clear();
        CONTACT.clear();
        LAST_POS.clear();
        PLAYER_VEL.clear();
    }

    /** Nombre total de passages depuis le démarrage (pour le test automatique). */
    public static int passages() {
        return passages;
    }

    /** À appeler une fois par tick et par dimension, avant {@link #tick}. */
    public static void trackPlayers(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            Vec3 now = p.position();
            Vec3 last = LAST_POS.put(p.getUUID(), now);
            Vec3 v = last == null ? Vec3.ZERO : now.subtract(last);
            if (v.lengthSqr() > 16) v = Vec3.ZERO; // téléporté autrement
            PLAYER_VEL.put(p.getUUID(), v);
        }
    }

    public static Vec3 velocity(Entity e) {
        if (e instanceof ServerPlayer p) return PLAYER_VEL.getOrDefault(p.getUUID(), Vec3.ZERO);
        return e.getDeltaMovement();
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

    private static boolean eligible(Entity e) {
        return e.isAlive() && !e.isSpectator() && !e.isPassenger() && !e.isVehicle()
            && !(e instanceof Display) && !(e instanceof HangingEntity) && !(e instanceof Marker)
            && !(e instanceof AreaEffectCloud) && !(e instanceof LightningBolt);
    }

    private static double half(AABB bb, Direction.Axis axis) {
        return switch (axis) {
            case X -> bb.getXsize() / 2;
            case Y -> bb.getYsize() / 2;
            case Z -> bb.getZsize() / 2;
        };
    }

    private static Direction.Axis axisOf(Vec3 v) {
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        return ax >= ay && ax >= az ? Direction.Axis.X : (ay >= az ? Direction.Axis.Y : Direction.Axis.Z);
    }

    private static void end(ServerLevel level, Fold f, Anchor here, long time) {
        if (!level.isLoaded(here.pos())) return;
        Direction face = here.face();
        AABB block = new AABB(here.pos());
        AABB search = block.expandTowards(face.getStepX() * REACH, face.getStepY() * REACH, face.getStepZ() * REACH)
            .inflate(face.getAxis() == Direction.Axis.X ? 0 : 1.0, face.getAxis() == Direction.Axis.Y ? 0 : 1.5,
                face.getAxis() == Direction.Axis.Z ? 0 : 1.0);
        List<Entity> list = level.getEntities((Entity) null, search, Transit::eligible);
        if (list.isEmpty()) return;
        Vec3 n = here.normal(), u = here.upVec(), r = here.rightVec(), c0 = here.center();
        Direction.Axis na = face.getAxis(), ua = here.up().getAxis(), ra = axisOf(r);
        for (Entity e : list) {
            AABB bb = e.getBoundingBox();
            Vec3 c = bb.getCenter();
            double s = c.subtract(c0).dot(n) - half(bb, na);
            if (s < -0.4) continue;
            Vec3 v = velocity(e);
            double vn = v.dot(n);
            long key = ((long) e.getId() << 21) ^ ((long) f.id << 1) ^ (here == f.a ? 0 : 1);
            Long lastTouch = CONTACT.get(key);
            boolean touchedBefore = lastTouch != null && lastTouch >= time - 1;

            double t;
            if (vn < -1e-4 && s + vn <= 0.05) t = Math.max(0, Math.min(1, s / -vn));
            else if (s <= 0.03 && !touchedBefore && !(e instanceof Projectile)) t = 0;
            else t = -1;

            Vec3 ci = t > 0 ? c.add(v.scale(t)) : c;
            Vec3 d = ci.subtract(c0);
            boolean over = Math.abs(d.dot(u)) < 0.5 + half(bb, ua) - 0.1 && Math.abs(d.dot(r)) < 0.5 + half(bb, ra) - 0.1;
            if (!over) continue;
            if (s <= 0.05) CONTACT.put(key, time);
            if (t < 0) continue;
            Long cd = COOLDOWNS.get(e.getId());
            if (cd != null && cd >= time) continue;
            pass(level, f, here, e, ci, v, time);
        }
    }

    /** Fait passer {@code e} par le pli. Renvoie faux si la sortie est bouchée. */
    public static boolean pass(ServerLevel level, Fold f, Anchor here, Entity e, Vec3 impactCenter, Vec3 v, long time) {
        Anchor there = f.other(here);
        AABB bb = e.getBoundingBox();
        double hx = bb.getXsize() / 2, hy = bb.getYsize() / 2, hz = bb.getZsize() / 2;
        Vec3 n2 = there.normal(), u2 = there.upVec(), r2 = there.rightVec();
        double out = half(bb, there.face().getAxis()) + 0.03;
        Vec3 base = Fold.mapPoint(here, there, impactCenter).add(n2.scale(out));

        Vec3 spot = null;
        double[] steps = {0, -0.25, 0.25, -0.5, 0.5, -0.75, 0.75, -1.0, 1.0, -1.25, 1.25};
        double[] side = {0, -0.3, 0.3};
        search:
        for (double du : steps) {
            for (double dr : side) {
                Vec3 cand = base.add(u2.scale(du)).add(r2.scale(dr));
                AABB box = new AABB(cand.x - hx, cand.y - hy, cand.z - hz, cand.x + hx, cand.y + hy, cand.z + hz);
                if (level.noCollision(e, box)) {
                    spot = cand;
                    break search;
                }
            }
        }
        COOLDOWNS.put(e.getId(), time + COOLDOWN);
        if (spot == null) {
            Effects.snag(level, here, f.color);
            return false;
        }

        Vec3 v2 = Fold.mapVector(here, there, v);
        Vec3 look = Fold.mapVector(here, there, e.getLookAngle());
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -look.y))));
        if (Math.abs(look.y) > 0.999) yaw = e.getYRot();
        double x = spot.x, y = spot.y - hy, z = spot.z;

        // l'entité ne doit pas retraverser aussitôt la face de sortie
        CONTACT.put(((long) e.getId() << 21) ^ ((long) f.id << 1) ^ (there == f.a ? 0 : 1), time);

        e.resetFallDistance();
        if (e instanceof ServerPlayer p) {
            Compat.teleportPlayer(p, level, x, y, z, yaw, pitch);
            LAST_POS.put(p.getUUID(), new Vec3(x, y, z));
            PLAYER_VEL.put(p.getUUID(), v2);
            p.setDeltaMovement(v2);
            Compat.syncMotion(p);
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
        if (com.ourlet.debug.ServerSelfTest.enabled()) TEST_EXITS.put(e.getId(), new Exit(new Vec3(x, y, z), v2));
        Effects.transit(level, here, there, f.color, bb.getSize());
        return true;
    }
}
