package com.ourlet.fold;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Les entonnoirs : un entonnoir qui se déverse dans un bloc cousu (par la face cousue) envoie ses
 * objets à travers le pli. De l'autre côté, s'il y a un conteneur juste devant la couture (coffre,
 * tonneau, autre entonnoir…), les objets y entrent directement ; sinon ils sont éjectés.
 * Un objet par entonnoir toutes les 8 ticks, comme un entonnoir vanilla ; si le conteneur est plein,
 * rien ne bouge (ça ne fait pas déborder).
 */
public final class Hoppers {

    private Hoppers() {}

    private static int delivered;

    public static void reset() {
        delivered = 0;
    }

    /** Objets transportés depuis le démarrage (pour le test automatique). */
    public static int delivered() {
        return delivered;
    }

    static void tick(ServerLevel level, List<Fold> folds, long time) {
        if (time % 8 != 0) return;
        String dim = com.ourlet.platform.Compat.dim(level);
        for (Fold f : folds) {
            for (Anchor here : new Anchor[]{f.a, f.b}) {
                if (!here.dim().equals(dim) || !f.canEnter(here)) continue;
                for (BlockPos cell : f.cells(here)) push(level, f, here, cell);
            }
        }
    }

    private static void push(ServerLevel level, Fold f, Anchor here, BlockPos cell) {
        BlockPos front = cell.relative(here.face());
        if (!level.isLoaded(front)) return;
        BlockState hs = level.getBlockState(front);
        if (!(hs.getBlock() instanceof HopperBlock)) return;
        Direction facing = hs.getValue(HopperBlock.FACING);
        if (facing != here.face().getOpposite() || !hs.getValue(HopperBlock.ENABLED)) return;
        if (!(level.getBlockEntity(front) instanceof HopperBlockEntity hopper)) return;
        int slot = -1;
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            if (!hopper.getItem(i).isEmpty()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;
        Anchor there = f.other(here);
        ServerLevel to = Folds.level(there);
        if (to == null) return;
        BlockPos outCell = f.mapCell(here, cell);
        BlockPos outPos = outCell.relative(there.face());
        if (!to.isLoaded(outPos)) return;
        ItemStack one = hopper.getItem(slot).copy();
        one.setCount(1);
        Container dest = HopperBlockEntity.getContainerAt(to, outPos);
        if (dest != null) {
            ItemStack rest = HopperBlockEntity.addItem(hopper, dest, one, there.face().getOpposite());
            if (!rest.isEmpty()) return; // plein : on attend
        } else {
            if (!to.getBlockState(outPos).getCollisionShape(to, outPos).isEmpty()) return; // bouché
            Vec3 at = there.faceCenter(outCell).add(there.normal().scale(0.3));
            ItemEntity item = new ItemEntity(to, at.x, at.y - 0.125, at.z, one);
            item.setDeltaMovement(there.normal().scale(0.15));
            item.setDefaultPickUpDelay();
            to.addFreshEntity(item);
        }
        hopper.removeItem(slot, 1);
        hopper.setChanged();
        delivered++;
        Effects.hopper(level, here, cell, f.color());
        Effects.hopper(to, there, outCell, f.color());
        var owner = Folds.server().getPlayerList().getPlayer(f.owner);
        if (owner != null) Progress.grant(owner, "delivery");
    }
}
