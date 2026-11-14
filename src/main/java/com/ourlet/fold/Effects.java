package com.ourlet.fold;

import com.ourlet.platform.Compat;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Particules (des bouts de fil, des lettres aspirées par la couture) et sons (du tissu) :
 * uniquement des éléments vanilla, envoyés par le serveur.
 */
public final class Effects {

    private Effects() {}

    /**
     * Couleurs des fils : rose, indigo, safran, sauge, fil de bâti, puis les 16 teintures
     * (blanc, orange, magenta, bleu clair, jaune, vert clair, rose, gris, gris clair, cyan,
     * violet, bleu, marron, vert, rouge, noir), en tons de coton à broder.
     */
    public static final int[] THREAD_RGB = {
        0xE07A95, 0x5458C2, 0xF0A92E, 0x7FAE88, 0xF4EEDC,
        0xF4F1EA, 0xE8873A, 0xC45BB8, 0x7DB7E0, 0xEDCB45, 0x8CC04F, 0xEE9DB5, 0x6E6A68,
        0xB3AEA8, 0x3C9EA6, 0x8355B8, 0x3F55A8, 0x8A5A3B, 0x4E7F3A, 0xC2413B, 0x2B2730};
    private static final int LINEN = 0xEDE3CF;
    /** Dernier froissement joué à chaque bout (une boucle sans fin ne doit pas faire un vacarme). */
    private static final java.util.Map<String, Long> LAST_SOUND = new java.util.HashMap<>();

    public static int rgb(int color) {
        return THREAD_RGB[Math.floorMod(color, THREAD_RGB.length)];
    }

    private static ParticleOptions thread(int color, float size) {
        return Compat.dust(rgb(color), size);
    }

    /** Les points du bord d'une couture de côté {@code size}, de 0 à 1 le long du tour. */
    private static Vec3 around(Anchor a, int size, double t, double lift) {
        double h = size / 2.0;
        Vec3 c = a.center().add(a.normal().scale(lift));
        Vec3 u = a.upVec(), r = a.rightVec();
        double side = t * 4;
        int k = (int) Math.min(3, Math.floor(side));
        double s = (side - k) * 2 * h - h;
        return switch (k) {
            case 0 -> c.add(u.scale(h)).add(r.scale(s));
            case 1 -> c.add(r.scale(h)).add(u.scale(-s));
            case 2 -> c.add(u.scale(-h)).add(r.scale(-s));
            default -> c.add(r.scale(-h)).add(u.scale(s));
        };
    }

    private static void border(ServerLevel level, Anchor a, int size, ParticleOptions p, int count, double lift) {
        for (int i = 0; i < count; i++) send(level, p, around(a, size, (i + 0.5) / count, lift));
    }

    private static void send(ServerLevel level, ParticleOptions p, Vec3 at) {
        level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    /** La couture se fait comme à la machine : le fil fait le tour de la face, point après point. */
    public static void sewingAnimation(Fold f) {
        for (Anchor a : new Anchor[]{f.a, f.b}) {
            ServerLevel level = Folds.level(a);
            if (level == null) continue;
            Folds.playAt(level, a, SoundEvents.WOOL_PLACE, 1.0f, 1.25f);
            int steps = 10, perStep = 3 * f.size;
            for (int k = 0; k < steps; k++) {
                final int step = k;
                Folds.later(k + 1, () -> {
                    for (int i = 0; i < perStep; i++) {
                        double t = (step + (i + 0.5) / perStep) / steps;
                        send(level, thread(f.color(), 1.1f), around(a, f.size, t, 0.06));
                    }
                    if (step % 3 == 0) Folds.playAt(level, a, SoundEvents.WOOL_STEP, 0.35f, 1.6f + step * 0.04f);
                });
            }
            Folds.later(steps + 1, () -> {
                Vec3 c = a.center().add(a.normal().scale(0.1));
                level.sendParticles(thread(f.color(), 0.8f), c.x, c.y, c.z, 10 * f.size, 0.25 * f.size, 0.25 * f.size,
                    0.25 * f.size, 0.02);
                Folds.playAt(level, a, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
            });
        }
    }

    public static void unstitch(ServerLevel level, Fold f, Anchor a, boolean torn) {
        Vec3 c = a.center().add(a.normal().scale(0.1));
        double spread = 0.35 * f.size;
        level.sendParticles(thread(f.color(), 1.0f), c.x, c.y, c.z, 24 * f.size, spread, spread, spread, 0.05);
        level.sendParticles(Compat.dust(LINEN, 0.7f), c.x, c.y, c.z, 10 * f.size, spread, spread, spread, 0.03);
        Folds.playAt(level, a, torn ? SoundEvents.WOOL_BREAK : SoundEvents.SHEEP_SHEAR, 1.0f, torn ? 0.6f : 1.1f);
    }

    /**
     * L'ambiance d'une couture, quand quelqu'un est assez près pour la voir : des lettres
     * (celles de la table d'enchantement) sont aspirées dans le pli ouvert, un brin de fil
     * s'échappe parfois, une couture élastique perd une goutte de slime, une couture au miel brille.
     */
    public static void idle(ServerLevel level, Fold f, String dim, long time) {
        for (Anchor a : new Anchor[]{f.a, f.b}) {
            if (!a.dim().equals(dim)) continue;
            Vec3 c = a.center();
            if (level.getNearestPlayer(c.x, c.y, c.z, 20, false) == null) continue;
            var rnd = level.getRandom();
            Vec3 n = a.normal(), u = a.upVec(), r = a.rightVec();
            double h = f.half();
            if (f.canEnter(a)) {
                for (int i = 0; i < f.size; i++) {
                    Vec3 target = c.add(u.scale((rnd.nextDouble() - 0.5) * h)).add(r.scale((rnd.nextDouble() - 0.5) * h));
                    Vec3 from = n.scale(0.8 + rnd.nextDouble() * 0.8).add(u.scale((rnd.nextDouble() - 0.5) * 1.6))
                        .add(r.scale((rnd.nextDouble() - 0.5) * 1.6));
                    // ENCHANT : la particule part de (cible + vitesse) et rejoint la cible
                    level.sendParticles(ParticleTypes.ENCHANT, target.x, target.y, target.z, 0, from.x, from.y, from.z, 1.0);
                }
            }
            if (time % 30 == 0) {
                Vec3 o = c.add(n.scale(0.08)).add(u.scale((rnd.nextDouble() - 0.5) * 2 * h)).add(r.scale((rnd.nextDouble() - 0.5) * 2 * h));
                send(level, thread(f.color(), 0.6f), o);
            }
            if (f.tension() != Fold.NORMAL && time % 20 == 0) {
                Vec3 o = c.add(n.scale(0.1)).add(u.scale((rnd.nextDouble() - 0.5) * 2 * h)).add(r.scale((rnd.nextDouble() - 0.5) * 2 * h));
                level.sendParticles(f.tension() == Fold.ELASTIC ? ParticleTypes.ITEM_SLIME : ParticleTypes.WAX_ON,
                    o.x, o.y, o.z, 2, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    /** Le pli se ferme (redstone) ou se rouvre : le fil se tend ou se relâche. */
    public static void tighten(ServerLevel level, Fold f, Anchor a, boolean closing) {
        border(level, a, f.size, thread(f.color(), 0.8f), 12 * f.size, 0.06);
        Folds.playAt(level, a, closing ? SoundEvents.WOOL_PLACE : SoundEvents.WOOL_STEP, 0.7f, closing ? 0.6f : 1.4f);
    }

    /** Les ciseaux changent le sens du pli. */
    public static void snip(ServerLevel level, Fold f, Anchor a) {
        border(level, a, f.size, thread(f.color(), 0.9f), 16 * f.size, 0.06);
        Folds.playAt(level, a, SoundEvents.SHEEP_SHEAR, 0.8f, 1.5f);
    }

    /** La teinture prend : le fil change de couleur dans un nuage de sa nouvelle teinte. */
    public static void dyed(ServerLevel level, Fold f, Anchor a) {
        Vec3 c = a.center().add(a.normal().scale(0.15));
        level.sendParticles(thread(f.color(), 1.2f), c.x, c.y, c.z, 30 * f.size, 0.3 * f.size, 0.3 * f.size, 0.3 * f.size, 0.06);
        border(level, a, f.size, thread(f.color(), 1.0f), 16 * f.size, 0.06);
        Folds.playAt(level, a, SoundEvents.DYE_USE, 1.0f, 1.0f);
    }

    /** Un nom brodé : quelques étincelles et le bruit d'une page tournée. */
    public static void named(ServerLevel level, Fold f, Anchor a) {
        Vec3 c = a.center().add(a.normal().scale(0.2));
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.3 * f.size, 0.3 * f.size, 0.3 * f.size, 0.01);
        Folds.playAt(level, a, SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
    }

    /** Slime (élastique) ou miel (amorti) sur le fil. */
    public static void tension(ServerLevel level, Fold f, Anchor a) {
        Vec3 c = a.center().add(a.normal().scale(0.15));
        boolean elastic = f.tension() == Fold.ELASTIC;
        level.sendParticles(elastic ? ParticleTypes.ITEM_SLIME : ParticleTypes.WAX_ON, c.x, c.y, c.z, 16 * f.size,
            0.3 * f.size, 0.3 * f.size, 0.3 * f.size, 0.05);
        Folds.playAt(level, a, f.tension() == Fold.DAMPED ? SoundEvents.HONEYCOMB_WAX_ON
            : (elastic ? SoundEvents.SLIME_SQUISH : SoundEvents.WOOL_STEP), 1.0f, 1.0f);
    }

    public static void pending(ServerLevel level, Anchor a, int size) {
        border(level, a, size, Compat.dust(LINEN, 0.9f), 12 * size, 0.05);
    }

    /** Un passage : une bouffée de fil aux deux bouts, un froissement de tissu d'autant plus aigu que ça va vite. */
    public static void transit(Fold f, Anchor from, Anchor to, double size, double speed) {
        float vol = (float) Math.min(1.0, 0.35 + size * 0.3);
        float pitch = (float) Math.min(2.0, 1.3 + speed * 0.35);
        for (Anchor a : new Anchor[]{from, to}) {
            ServerLevel level = Folds.level(a);
            if (level == null) continue;
            Vec3 c = a.center().add(a.normal().scale(0.2));
            level.sendParticles(thread(f.color(), 0.9f), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.04);
            String key = f.id + (a == f.a ? "a" : "b");
            long now = Folds.now();
            Long last = LAST_SOUND.get(key);
            if (last == null || now - last >= 4) {
                LAST_SOUND.put(key, now);
                Folds.playAt(level, a, SoundEvents.WOOL_BREAK, vol, pitch);
                if (f.tension() == Fold.ELASTIC && a == to) Folds.playAt(level, a, SoundEvents.SLIME_SQUISH, vol, 1.2f);
            }
            Markers.ripple(f, a);
        }
    }

    /** Le ruban de fil qui suit une entité juste après son passage. */
    public static void trail(ServerLevel level, Vec3 at, int color) {
        level.sendParticles(thread(color, 0.75f), at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
    }

    /** Le passage est bouché de l'autre côté. */
    public static void snag(ServerLevel level, Fold f, Anchor a) {
        Vec3 c = a.center().add(a.normal().scale(0.1));
        level.sendParticles(thread(f.color(), 0.7f), c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.0);
        Folds.playAt(level, a, SoundEvents.WOOL_STEP, 0.5f, 0.7f);
    }

    /** Un objet passe par un entonnoir à travers le pli : deux brins de fil, pas de bruit (ça peut aller vite). */
    public static void hopper(ServerLevel level, Anchor a, net.minecraft.core.BlockPos cell, int color) {
        Vec3 c = a.faceCenter(cell).add(a.normal().scale(0.2));
        level.sendParticles(thread(color, 0.7f), c.x, c.y, c.z, 2, 0.15, 0.15, 0.15, 0.0);
    }

    /** Le fil d'Ariane : une ligne de points, visible du seul joueur, vers {@code target}. */
    public static void ariadne(ServerPlayer p, Vec3 target, int rgb) {
        Vec3 eye = p.getEyePosition().add(0, -0.45, 0);
        Vec3 d = target.subtract(eye);
        double dist = d.length();
        if (dist < 2.5) return;
        Vec3 dir = d.scale(1 / dist);
        ServerLevel level = Compat.level(p);
        long phase = level.getGameTime() / 10 % 3;
        for (int i = 0; i < 7; i++) {
            double k = 1.2 + i * 0.7 + phase * 0.23;
            if (k > dist - 1) break;
            Vec3 at = eye.add(dir.scale(k));
            Compat.particleFor(p, Compat.dust(rgb, 0.6f), at);
        }
    }
}
