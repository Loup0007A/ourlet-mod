package com.ourlet.fold;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Un pli : deux faces cousues ensemble. Ce qui entre par l'une ressort par l'autre.
 *
 * Le passage est une rotation propre (comme un pas dans l'embrasure d'une porte) :
 * la normale entrante de la face d'entrée devient la normale sortante de la face de sortie,
 * le « haut » reste le haut, la « droite » s'inverse pour garder un repère direct.
 * Une grande couture (bobine) fait 3×3 blocs de chaque côté.
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

    /** Tension du fil : normale, élastique (slime : on ressort plus vite), amortie (miel : on ressort au ralenti). */
    public static final int NORMAL = 0, ELASTIC = 1, DAMPED = 2;

    /** Couleur du fil de bâti (plis de poche, temporaires). */
    public static final int BASTING = 4;
    /** Première couleur de teinture : 5 + numéro de la teinture vanilla (blanc = 5 … noir = 20). */
    public static final int DYE_BASE = 5;

    public final int id;
    public final UUID owner;
    public final Anchor a;
    public final Anchor b;
    /** Côté de la couture, en blocs (1, ou 3 pour une grande couture). */
    public final int size;
    int color;
    /** Mode choisi aux ciseaux. */
    int mode = OPEN;
    int tension = NORMAL;
    /** Nom donné avec une étiquette (ou null). */
    String name;
    /** Un des deux blocs reçoit un signal de redstone : le pli est fermé tant que ça dure. */
    boolean powered;
    /** Pli de poche (dé à coudre) : il se défait seul à ce tick ; -1 sinon. */
    long expires = -1;

    Fold(int id, UUID owner, Anchor a, Anchor b, int color, int size) {
        this.id = id;
        this.owner = owner;
        this.a = a;
        this.b = b;
        this.color = color;
        this.size = size;
    }

    public Anchor other(Anchor end) {
        return end == a ? b : a;
    }

    public String tag() {
        return "ourlet_f" + id;
    }

    public int color() {
        return color;
    }

    public int mode() {
        return mode;
    }

    public int tension() {
        return tension;
    }

    public String name() {
        return name;
    }

    public boolean powered() {
        return powered;
    }

    public boolean temporary() {
        return expires >= 0;
    }

    public long expires() {
        return expires;
    }

    public boolean crossDimension() {
        return !a.dim().equals(b.dim());
    }

    /** Demi-côté de la couture (0,5 pour une couture simple, 1,5 pour une grande). */
    public double half() {
        return size / 2.0;
    }

    public List<BlockPos> cells(Anchor end) {
        return end.cells(size);
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
     * Les coordonnées dans la face sont bornées au carré de la couture (demi-côté {@code half}).
     */
    public static Vec3 mapPoint(Anchor from, Anchor to, Vec3 p, double half) {
        Vec3 d = p.subtract(from.center());
        double u = clamp(d.dot(from.upVec()), half), r = clamp(d.dot(from.rightVec()), half);
        return to.center().add(to.upVec().scale(u)).add(to.rightVec().scale(-r));
    }

    public Vec3 mapPoint(Anchor from, Vec3 p) {
        return mapPoint(from, other(from), p, half());
    }

    /** Le bloc d'en face d'un bloc de la couture (même place dans le carré, droite inversée). */
    public BlockPos mapCell(Anchor from, BlockPos cell) {
        Anchor to = other(from);
        int dx = cell.getX() - from.pos().getX(), dy = cell.getY() - from.pos().getY(), dz = cell.getZ() - from.pos().getZ();
        var u = from.up();
        var r = from.right();
        int du = dx * u.getStepX() + dy * u.getStepY() + dz * u.getStepZ();
        int dr = dx * r.getStepX() + dy * r.getStepY() + dz * r.getStepZ();
        return to.pos().relative(to.up(), du).relative(to.right(), -dr);
    }

    private static double clamp(double v, double h) {
        return Math.max(-h, Math.min(h, v));
    }

    String serialize() {
        String n = name == null ? "" : URLEncoder.encode(name, StandardCharsets.UTF_8);
        return id + ";" + owner + ";" + a.dim() + ";" + a.serialize() + ";" + b.serialize() + ";" + color
            + ";false;false;" + mode + ";" + size + ";" + tension + ";" + n;
    }

    /** Lit une ligne ; {@code legacyForced} reçoit les tronçons forcés par les versions d'avant 2.0. */
    static Fold parse(String line, java.util.function.Consumer<Anchor> legacyForced) {
        String[] p = line.split(";", -1);
        int size = p.length > 9 && !p[9].isEmpty() ? Integer.parseInt(p[9]) : 1;
        Fold f = new Fold(Integer.parseInt(p[0]), UUID.fromString(p[1]), Anchor.parse(p[3], p[2]), Anchor.parse(p[4], p[2]),
            Integer.parseInt(p[5]), size == 3 ? 3 : 1);
        if (Boolean.parseBoolean(p[6])) legacyForced.accept(f.a);
        if (Boolean.parseBoolean(p[7])) legacyForced.accept(f.b);
        if (p.length > 8 && !p[8].isEmpty()) f.mode = Integer.parseInt(p[8]);
        if (p.length > 10 && !p[10].isEmpty()) f.tension = Integer.parseInt(p[10]);
        if (p.length > 11 && !p[11].isEmpty()) f.name = URLDecoder.decode(p[11], StandardCharsets.UTF_8);
        return f;
    }
}
