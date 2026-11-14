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

    /** Ouvert dans les deux sens. */
    public static final int OPEN = 0;
    /** Sens unique : on n'entre que par A. */
    public static final int ONLY_A = 1;
    /** Sens unique : on n'entre que par B. */
    public static final int ONLY_B = 2;
    /** Fermé aux ciseaux (la couture reste, rien ne passe). */
    public static final int CLOSED = 3;

    /** Couleur du fil de bâti (plis de poche, temporaires). */
    public static final int BASTING = 4;

    public final int id;
    public final UUID owner;
    public final Anchor a;
    public final Anchor b;
    public final int color;
    /** Mode choisi aux ciseaux. */
    int mode = OPEN;
    /** Un des deux blocs reçoit un signal de redstone : le pli est fermé tant que ça dure. */
    boolean powered;
    /** Pli de poche (dé à coudre) : il se défait seul à ce tick ; -1 sinon. */
    long expires = -1;
    /** Vrai si c'est Ourlet qui a forcé le chargement du tronçon de A / de B. */
    boolean forcedA, forcedB;

    Fold(int id, UUID owner, Anchor a, Anchor b, int color) {
        this.id = id;
        this.owner = owner;
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

    public int mode() {
        return mode;
    }

    public boolean powered() {
        return powered;
    }

    public boolean temporary() {
        return expires >= 0;
    }

    public boolean crossDimension() {
        return !a.dim().equals(b.dim());
    }

    /** Peut-on entrer dans le pli par ce bout ? */
    public boolean canEnter(Anchor end) {
        if (powered || mode == CLOSED) return false;
        return mode == OPEN || (mode == ONLY_A && end == a) || (mode == ONLY_B && end == b);
    }

    /** Taille de la couture visible à ce bout : elle se resserre quand le pli se ferme. */
    public float scaleAt(Anchor end) {
        if (powered || mode == CLOSED) return 0.45f;
        if (mode == OPEN) return 0.998f;
        return canEnter(end) ? 0.998f : 0.7f;
    }

    /** Distance entre les deux bouts, en blocs (-1 entre deux dimensions). */
    public int span() {
        if (crossDimension()) return -1;
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
        return id + ";" + owner + ";" + a.dim() + ";" + a.serialize() + ";" + b.serialize() + ";" + color
            + ";" + forcedA + ";" + forcedB + ";" + mode;
    }

    static Fold parse(String line) {
        String[] p = line.split(";");
        Fold f = new Fold(Integer.parseInt(p[0]), UUID.fromString(p[1]), Anchor.parse(p[3], p[2]), Anchor.parse(p[4], p[2]),
            Integer.parseInt(p[5]));
        f.forcedA = Boolean.parseBoolean(p[6]);
        f.forcedB = Boolean.parseBoolean(p[7]);
        if (p.length > 8) f.mode = Integer.parseInt(p[8]);
        return f;
    }
}
