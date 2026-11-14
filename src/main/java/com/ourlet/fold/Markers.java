package com.ourlet.fold;

import com.ourlet.platform.Compat;
import com.ourlet.registry.OurletItems;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * La couture visible : un affichage d'objet vanilla (item_display) plaqué sur chaque face cousue
 * (9 pour une grande couture, comme un patchwork), qui montre le fil brodé. Aucun code de rendu
 * côté client : la couture est créée par /summon et animée par /data merge (interpolation vanilla).
 */
public final class Markers {

    private Markers() {}

    public static final String TAG = "ourlet";

    /** Dernière ondulation de chaque bout (pour ne pas en lancer une à chaque tick d'une chute sans fin). */
    private static final Map<String, Long> LAST_RIPPLE = new HashMap<>();

    public static void place(Fold f) {
        summon(f, f.a);
        summon(f, f.b);
    }

    private static Vec3 spot(Anchor a, BlockPos cell) {
        return a.faceCenter(cell).add(a.normal().scale(0.012));
    }

    private static String transformation(float s) {
        return String.format(Locale.ROOT,
            "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.3ff,%.3ff,0.06f]}",
            s, s);
    }

    private static void summon(Fold f, Anchor a) {
        ServerLevel level = Folds.level(a);
        if (level == null) return;
        Vec3 n = a.normal();
        float yaw = (float) Math.toDegrees(Math.atan2(-n.x, n.z));
        float pitch = (float) Math.toDegrees(Math.asin(-n.y));
        for (BlockPos cell : f.cells(a)) {
            Vec3 p = spot(a, cell);
            String nbt = String.format(Locale.ROOT,
                "{Tags:[\"%s\",\"%s\"],Rotation:[%.1ff,%.1ff],item:%s,brightness:{sky:15,block:15},view_range:0.75f,%s}",
                TAG, f.tag(), yaw, pitch, Compat.itemNbt(OurletItems.threadId(f.color())), transformation(f.scaleAt(a)));
            Compat.command(level, String.format(Locale.ROOT, "summon minecraft:item_display %.4f %.4f %.4f %s", p.x, p.y, p.z, nbt));
        }
    }

    private static void scaleTo(Fold f, Anchor a, float s, int ticks) {
        ServerLevel level = Folds.level(a);
        if (level == null) return;
        for (BlockPos cell : f.cells(a)) {
            Vec3 p = spot(a, cell);
            Compat.command(level, String.format(Locale.ROOT,
                "data merge entity @e[type=minecraft:item_display,tag=%s,x=%.4f,y=%.4f,z=%.4f,distance=..0.3,limit=1] {%s,interpolation_duration:%d,start_interpolation:0}",
                f.tag(), p.x, p.y, p.z, transformation(s), ticks));
        }
    }

    /** Met la taille des coutures à jour (ouvert / sens unique / fermé), avec une petite animation. */
    public static void refresh(Fold f) {
        scaleTo(f, f.a, f.scaleAt(f.a), 6);
        scaleTo(f, f.b, f.scaleAt(f.b), 6);
    }

    /** Une onde dans le tissu : la couture gonfle un instant quand quelque chose la traverse. */
    public static void ripple(Fold f, Anchor a) {
        String key = f.id + (a == f.a ? "a" : "b");
        long now = Folds.now();
        Long last = LAST_RIPPLE.get(key);
        if (last != null && now - last < 8) return;
        LAST_RIPPLE.put(key, now);
        float base = f.scaleAt(a);
        scaleTo(f, a, Math.min(1.25f, base * 1.22f), 2);
        Folds.later(3, () -> {
            if (Folds.all().contains(f)) scaleTo(f, a, f.scaleAt(a), 5);
        });
    }

    public static void remove(Fold f) {
        for (Anchor a : new Anchor[]{f.a, f.b}) {
            ServerLevel level = Folds.level(a);
            if (level == null) continue;
            for (Entity e : find(level, f, a)) e.discard();
            // au cas où un marqueur aurait été déplacé
            Compat.command(level, "kill @e[type=minecraft:item_display,tag=" + f.tag() + "]");
        }
    }

    private static List<Display.ItemDisplay> find(ServerLevel level, Fold f, Anchor a) {
        AABB box = new AABB(a.pos()).inflate(f.size / 2 + 1.0);
        String tag = f.tag();
        Vec3 c = a.center(), n = a.normal(), u = a.upVec(), r = a.rightVec();
        double h = f.half() + 0.1;
        // seulement les coutures posées sur ce bout (l'autre bout peut être tout près)
        return level.getEntitiesOfClass(Display.ItemDisplay.class, box, e -> {
            if (!Compat.tags(e).contains(tag)) return false;
            Vec3 d = e.position().subtract(c);
            return Math.abs(d.dot(n) - 0.012) < 0.08 && Math.abs(d.dot(u)) <= h && Math.abs(d.dot(r)) <= h;
        });
    }

    /** Remet les coutures de ce monde si elles ont disparu, retire les doublons. */
    public static void heal(ServerLevel level, Fold f) {
        String dim = Compat.dim(level);
        int expected = f.size * f.size;
        for (Anchor a : new Anchor[]{f.a, f.b}) {
            if (!a.dim().equals(dim) || !Compat.entitiesLoaded(level, a.pos())) continue;
            List<Display.ItemDisplay> found = find(level, f, a);
            if (found.size() == expected) continue;
            for (Display.ItemDisplay d : found) d.discard();
            summon(f, a);
        }
    }

    public static int count(Fold f) {
        int n = 0;
        for (Anchor a : new Anchor[]{f.a, f.b}) {
            ServerLevel level = Folds.level(a);
            if (level != null) n += find(level, f, a).size();
        }
        return n;
    }

    /** Une couture dont le pli n'existe plus (pli décousu pendant que son tronçon dormait) : on la retire. */
    public static boolean isOrphan(Entity e) {
        if (!(e instanceof Display.ItemDisplay) || Folds.server() == null) return false;
        var tags = Compat.tags(e);
        if (!tags.contains(TAG)) return false;
        for (String t : tags) {
            if (t.startsWith("ourlet_f")) {
                try {
                    Fold f = Folds.byId(Integer.parseInt(t.substring(8)));
                    if (f == null) return true;
                    String dim = Compat.dim(e.level());
                    return !f.a.dim().equals(dim) && !f.b.dim().equals(dim);
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }
        return true;
    }
}
