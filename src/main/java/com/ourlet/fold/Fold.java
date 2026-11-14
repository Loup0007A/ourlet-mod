package com.ourlet.fold;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Un pli : deux faces cousues ensemble. Ce qui entre par l'une ressort par l'autre.
 *
 * Le passage est une rotation propre (comme un pas dans l'embrasure d'une porte) :
 * la normale entrante de la face d'entrée devient la normale sortante de la face de sortie,
 * le « haut » reste le haut, la « droite » s'inverse pour garder un repère direct.
 */
public final class Fold {

    public final int id;
    public final UUID owner;
    public final String dimension;
    public final Anchor a;
    public final Anchor b;
    public final int color;
    /** Vrai si c'est Ourlet qui a forcé le chargement du tronçon de A / de B. */
    boolean forcedA, forcedB;

    Fold(int id, UUID owner, String dimension, Anchor a, Anchor b, int color) {
        this.id = id;
        this.owner = owner;
        this.dimension = dimension;
        this.a = a;
        this.b = b;
        this.color = color;
    }

    public Anchor other(Anchor end) {
        return end == a ? b : a;
    }

    public String tag() {
        return "ourlet_f" + id;
    }

    /** Distance entre les deux bouts, en blocs. */
    public int span() {
        return (int) Math.round(Math.sqrt(a.pos().distSqr(b.pos())));
    }

    /** Un vecteur (vitesse, regard) qui entre par {@code from} et ressort par {@code to}. */
    public static Vec3 mapVector(Anchor from, Anchor to, Vec3 w) {
        double wn = w.dot(from.normal()), wu = w.dot(from.upVec()), wr = w.dot(from.rightVec());
        return to.normal().scale(-wn).add(to.upVec().scale(wu)).add(to.rightVec().scale(-wr));
    }

    /**
     * Un point de la face {@code from} : sa position correspondante sur la face {@code to}.
     * Les coordonnées dans la face sont bornées au carré de la face.
     */
    public static Vec3 mapPoint(Anchor from, Anchor to, Vec3 p) {
        Vec3 d = p.subtract(from.center());
        double u = clamp(d.dot(from.upVec())), r = clamp(d.dot(from.rightVec()));
        return to.center().add(to.upVec().scale(u)).add(to.rightVec().scale(-r));
    }

    private static double clamp(double v) {
        return Math.max(-0.5, Math.min(0.5, v));
    }

    String serialize() {
        return id + ";" + owner + ";" + dimension + ";" + a.serialize() + ";" + b.serialize() + ";" + color
            + ";" + forcedA + ";" + forcedB;
    }

    static Fold parse(String line) {
        String[] p = line.split(";");
        Fold f = new Fold(Integer.parseInt(p[0]), UUID.fromString(p[1]), p[2], Anchor.parse(p[3]), Anchor.parse(p[4]),
            Integer.parseInt(p[5]));
        f.forcedA = Boolean.parseBoolean(p[6]);
        f.forcedB = Boolean.parseBoolean(p[7]);
        return f;
    }
}
