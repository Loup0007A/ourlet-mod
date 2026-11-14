package com.ourlet.fold;

import com.ourlet.platform.Compat;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/** Particules (des bouts de fil) et sons (du tissu) : uniquement des éléments vanilla, envoyés par le serveur. */
public final class Effects {

    private Effects() {}

    /** Rose, indigo, safran, sauge : les couleurs des fils, comme sur un canevas. */
    public static final int[] THREAD_RGB = {0xE07A95, 0x5458C2, 0xF0A92E, 0x7FAE88};
    private static final int LINEN = 0xEDE3CF;

    private static ParticleOptions thread(int color, float size) {
        return Compat.dust(THREAD_RGB[Math.floorMod(color, THREAD_RGB.length)], size);
    }

    /** Points sur le bord de la face, comme des points de couture. */
    private static void border(ServerLevel level, Anchor a, ParticleOptions p, int perSide, double lift) {
        Vec3 c = a.center().add(a.normal().scale(lift));
        Vec3 u = a.upVec(), r = a.rightVec();
        for (int i = 0; i < perSide; i++) {
            double t = -0.5 + (i + 0.5) / perSide;
            send(level, p, c.add(u.scale(0.5)).add(r.scale(t)));
            send(level, p, c.add(u.scale(-0.5)).add(r.scale(t)));
            send(level, p, c.add(r.scale(0.5)).add(u.scale(t)));
            send(level, p, c.add(r.scale(-0.5)).add(u.scale(t)));
        }
    }

    private static void send(ServerLevel level, ParticleOptions p, Vec3 at) {
        level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    public static void stitch(ServerLevel level, Anchor a, int color) {
        border(level, a, thread(color, 1.1f), 6, 0.06);
        Vec3 c = a.center();
        level.sendParticles(thread(color, 0.8f), c.x, c.y, c.z, 14, 0.25, 0.25, 0.25, 0.02);
        Folds.playAt(level, a, SoundEvents.WOOL_PLACE, 1.0f, 1.25f);
        Folds.playAt(level, a, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
    }

    public static void unstitch(ServerLevel level, Anchor a, int color, boolean torn) {
        Vec3 c = a.center().add(a.normal().scale(0.1));
        level.sendParticles(thread(color, 1.0f), c.x, c.y, c.z, 24, 0.35, 0.35, 0.35, 0.05);
        level.sendParticles(Compat.dust(LINEN, 0.7f), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.03);
        Folds.playAt(level, a, torn ? SoundEvents.WOOL_BREAK : SoundEvents.SHEEP_SHEAR, 1.0f, torn ? 0.6f : 1.1f);
    }

    /** De temps en temps, un brin de fil s'échappe de la couture. */
    public static void idle(ServerLevel level, Fold f) {
        for (Anchor a : new Anchor[]{f.a, f.b}) {
            Vec3 c = a.center();
            if (level.getNearestPlayer(c.x, c.y, c.z, 24, false) == null) continue;
            Vec3 o = c.add(a.normal().scale(0.08)).add(a.upVec().scale(level.getRandom().nextDouble() - 0.5))
                .add(a.rightVec().scale(level.getRandom().nextDouble() - 0.5));
            send(level, thread(f.color, 0.6f), o);
        }
    }

    public static void pending(ServerLevel level, Anchor a) {
        border(level, a, Compat.dust(LINEN, 0.9f), 3, 0.05);
    }

    public static void transit(ServerLevel level, Anchor from, Anchor to, int color, double size) {
        float vol = (float) Math.min(1.0, 0.35 + size * 0.3);
        for (Anchor a : new Anchor[]{from, to}) {
            Vec3 c = a.center().add(a.normal().scale(0.2));
            level.sendParticles(thread(color, 0.9f), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.04);
            Folds.playAt(level, a, SoundEvents.WOOL_BREAK, vol, 1.7f);
        }
    }

    /** Le passage est bouché de l'autre côté. */
    public static void snag(ServerLevel level, Anchor a, int color) {
        Vec3 c = a.center().add(a.normal().scale(0.1));
        level.sendParticles(thread(color, 0.7f), c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.0);
        Folds.playAt(level, a, SoundEvents.WOOL_STEP, 0.5f, 0.7f);
    }
}
