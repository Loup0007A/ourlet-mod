package com.ourlet.fold;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Un bout de couture : une face d'un bloc, dans une dimension.
 *
 * {@code up} fixe l'orientation de la face (son « haut ») : UP pour une face latérale,
 * la direction où regardait le joueur pour une face de dessus ou de dessous. Avec la normale,
 * il donne un repère (n, u, r) qui permet de faire correspondre deux faces quelconques.
 * Pour une grande couture (3×3), {@code pos} est le bloc du centre.
 */
public record Anchor(String dim, BlockPos pos, Direction face, Direction up, String block) {

    public Vec3 normal() {
        return vec(face);
    }

    public Vec3 upVec() {
        return vec(up);
    }

    /** r = n × u : la « droite » de la face. */
    public Vec3 rightVec() {
        return normal().cross(upVec());
    }

    /** La « droite » de la face, comme direction de bloc. */
    public Direction right() {
        Vec3 r = rightVec();
        for (Direction d : Direction.values()) {
            if (d.getStepX() == (int) Math.round(r.x) && d.getStepY() == (int) Math.round(r.y)
                && d.getStepZ() == (int) Math.round(r.z)) return d;
        }
        return Direction.EAST;
    }

    /** Centre de la face (sur la surface du bloc). */
    public Vec3 center() {
        return faceCenter(pos);
    }

    /** Centre de la face d'un bloc quelconque du même plan. */
    public Vec3 faceCenter(BlockPos p) {
        return new Vec3(p.getX() + 0.5 + face.getStepX() * 0.5,
            p.getY() + 0.5 + face.getStepY() * 0.5,
            p.getZ() + 0.5 + face.getStepZ() * 0.5);
    }

    /** Les blocs d'une couture de côté {@code size} (1 ou 3), centrée sur {@code pos}. */
    public List<BlockPos> cells(int size) {
        List<BlockPos> out = new ArrayList<>();
        int h = size / 2;
        Direction r = right();
        for (int i = -h; i <= h; i++) {
            for (int j = -h; j <= h; j++) out.add(pos.relative(up, i).relative(r, j));
        }
        return out;
    }

    /** Le bloc {@code p} fait-il partie de cette couture (même plan, dans le carré) ? */
    public boolean covers(String d, BlockPos p, Direction f, int size) {
        if (!dim.equals(d) || face != f) return false;
        if (size <= 1) return pos.equals(p);
        int dx = p.getX() - pos.getX(), dy = p.getY() - pos.getY(), dz = p.getZ() - pos.getZ();
        int dn = dx * face.getStepX() + dy * face.getStepY() + dz * face.getStepZ();
        if (dn != 0) return false;
        Direction r = right();
        int du = dx * up.getStepX() + dy * up.getStepY() + dz * up.getStepZ();
        int dr = dx * r.getStepX() + dy * r.getStepY() + dz * r.getStepZ();
        int h = size / 2;
        return Math.abs(du) <= h && Math.abs(dr) <= h;
    }

    public boolean is(String d, BlockPos p, Direction f) {
        return dim.equals(d) && pos.equals(p) && face == f;
    }

    public static Vec3 vec(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** Le « haut » d'une face : UP pour une face latérale, sinon la direction horizontale donnée. */
    public static Direction upFor(Direction face, Direction horizontalFacing) {
        if (face.getAxis() != Direction.Axis.Y) return Direction.UP;
        return horizontalFacing.getAxis() == Direction.Axis.Y ? Direction.NORTH : horizontalFacing;
    }

    String serialize() {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ() + "," + face.getName() + "," + up.getName() + "," + block
            + "," + dim;
    }

    /** {@code defaultDim} : les fichiers de la version 1.0 n'avaient qu'une dimension par pli. */
    static Anchor parse(String s, String defaultDim) {
        String[] p = s.split(",");
        return new Anchor(p.length > 6 ? p[6] : defaultDim,
            new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])),
            Direction.byName(p[3]), Direction.byName(p[4]), p[5]);
    }
}
