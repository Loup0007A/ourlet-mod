package com.ourlet.fold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Un bout de couture : une face d'un bloc, dans une dimension.
 *
 * {@code up} fixe l'orientation de la face (son « haut ») : UP pour une face latérale,
 * la direction où regardait le joueur pour une face de dessus ou de dessous. Avec la normale,
 * il donne un repère (n, u, r) qui permet de faire correspondre deux faces quelconques.
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

    /** Centre de la face (sur la surface du bloc). */
    public Vec3 center() {
        return new Vec3(pos.getX() + 0.5 + face.getStepX() * 0.5,
            pos.getY() + 0.5 + face.getStepY() * 0.5,
            pos.getZ() + 0.5 + face.getStepZ() * 0.5);
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
