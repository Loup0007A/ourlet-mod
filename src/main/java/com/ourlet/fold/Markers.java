package com.ourlet.fold;

import com.ourlet.platform.Compat;
import com.ourlet.registry.OurletItems;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * La couture visible : un affichage d'objet vanilla (item_display) plaqué sur chaque face,
 * qui montre le fil brodé. Aucun code de rendu côté client : n'importe quel client qui a
 * les textures du mod la voit, et elle est créée par une simple commande /summon.
 */
public final class Markers {

    private Markers() {}

    public static final String TAG = "ourlet";

    public static void place(ServerLevel level, Fold f) {
        summon(level, f, f.a);
        summon(level, f, f.b);
    }

    private static void summon(ServerLevel level, Fold f, Anchor a) {
        Vec3 n = a.normal();
        Vec3 p = a.center().add(n.scale(0.012));
        float yaw = (float) Math.toDegrees(Math.atan2(-n.x, n.z));
        float pitch = (float) Math.toDegrees(Math.asin(-n.y));
        String nbt = String.format(Locale.ROOT,
            "{Tags:[\"%s\",\"%s\"],Rotation:[%.1ff,%.1ff],item:%s,brightness:{sky:15,block:15},view_range:0.75f,"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.998f,0.998f,0.06f]}}",
            TAG, f.tag(), yaw, pitch, Compat.itemNbt(OurletItems.threadId(f.color)));
        Compat.command(level, String.format(Locale.ROOT, "summon minecraft:item_display %.4f %.4f %.4f %s", p.x, p.y, p.z, nbt));
    }

    public static void remove(ServerLevel level, Fold f) {
        for (Anchor a : new Anchor[]{f.a, f.b}) {
            for (Entity e : find(level, f, a)) e.discard();
        }
        // au cas où un marqueur aurait été déplacé
        Compat.command(level, "kill @e[type=minecraft:item_display,tag=" + f.tag() + "]");
    }

    private static List<Display.ItemDisplay> find(ServerLevel level, Fold f, Anchor a) {
        AABB box = new AABB(a.pos()).inflate(1.0);
        String tag = f.tag();
        return level.getEntitiesOfClass(Display.ItemDisplay.class, box, e -> Compat.tags(e).contains(tag));
    }

    /** Remet la couture si elle a disparu, retire les doublons. */
    public static void heal(ServerLevel level, Fold f) {
        for (Anchor a : new Anchor[]{f.a, f.b}) {
            if (!Compat.entitiesLoaded(level, a.pos())) continue;
            List<Display.ItemDisplay> found = find(level, f, a);
            if (found.isEmpty()) summon(level, f, a);
            for (int i = 1; i < found.size(); i++) found.get(i).discard();
        }
    }

    public static int count(ServerLevel level, Fold f) {
        return find(level, f, f.a).size() + find(level, f, f.b).size();
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
                    return f == null || !f.dimension.equals(Compat.dim(e.level()));
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }
        return true;
    }
}
