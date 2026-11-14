package com.ourlet.fold;

import com.ourlet.platform.Compat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * La manipulation à travers le pli : le regard du joueur traverse la face cousue et continue
 * de l'autre côté. Ce qu'il casse ou utilise sur la face A agit sur ce qu'il « voit » derrière B.
 *
 * Casser à travers un pli : chaque fois que le bloc cousu (A) cède, le fil tire sur le bloc visé
 * de l'autre côté, en proportion de sa dureté. Le bloc A ne casse jamais par sa face cousue,
 * et ce qui tombe est ramené par le fil jusqu'au joueur.
 */
public final class Relay {

    private Relay() {}

    private static boolean busy;

    private record Dig(BlockPos pos, float progress) {}

    private static final Map<UUID, Dig> DIGS = new HashMap<>();

    public static void reset() {
        DIGS.clear();
        busy = false;
    }

    /** Vrai pendant qu'Ourlet relaie une action (pour ne pas se relayer lui-même). */
    public static boolean busy() {
        return busy;
    }

    public static void forget(UUID player) {
        DIGS.remove(player);
    }

    /** Le bloc que vise le joueur (côté serveur), ou null. */
    public static BlockHitResult pick(ServerPlayer player) {
        HitResult h = player.pick(Compat.reach(player) + 0.5, 1.0f, false);
        return h instanceof BlockHitResult bh && h.getType() == HitResult.Type.BLOCK ? bh : null;
    }

    /** Le point visé de l'autre côté du pli : le rayon du regard, prolongé depuis la face B. */
    public static BlockHitResult beyond(ServerPlayer player, Folds.End end, Vec3 hitOnFace) {
        Anchor here = end.here(), there = end.there();
        Vec3 eye = player.getEyePosition();
        Vec3 dir = hitOnFace.subtract(eye);
        if (dir.lengthSqr() < 1e-6) dir = player.getLookAngle();
        dir = dir.normalize();
        double left = Math.max(3.0, Compat.reach(player) - eye.distanceTo(hitOnFace));
        Vec3 start = end.fold().mapPoint(here, hitOnFace).add(there.normal().scale(0.002));
        Vec3 d2 = Fold.mapVector(here, there, dir).normalize();
        ServerLevel level = Folds.level(there);
        if (level == null) return null;
        BlockHitResult hit = level.clip(new ClipContext(start, start.add(d2.scale(left)), ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? null : hit;
    }

    /** Clic droit sur une face cousue : on utilise l'objet tenu de l'autre côté. */
    public static void use(ServerPlayer player, InteractionHand hand, Folds.End end, Vec3 hitOnFace) {
        BlockHitResult target = beyond(player, end, hitOnFace);
        if (target == null) {
            Compat.actionBar(player, Component.translatable("ourlet.relay.nothing"));
            return;
        }
        ServerLevel level = Folds.level(end.there());
        busy = true;
        try {
            player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, target);
        } finally {
            busy = false;
        }
        //#if !mc26
        player.swing(hand);
        //#endif
    }

    /**
     * Le bloc A vient de céder par sa face cousue : le fil tire sur le bloc d'en face.
     * Renvoie vrai si l'action a été prise en charge (le bloc A doit alors rester en place).
     */
    public static boolean dig(ServerPlayer player, Folds.End end, Vec3 hitOnFace) {
        ServerLevel level = Folds.level(end.there());
        ServerLevel home = Compat.level(player);
        BlockHitResult target = level == null ? null : beyond(player, end, hitOnFace);
        if (target == null || level.getBlockState(target.getBlockPos()).isAir()) {
            Compat.actionBar(player, Component.translatable("ourlet.relay.nothing"));
            return true;
        }
        BlockPos rp = target.getBlockPos();
        BlockState rs = level.getBlockState(rp);
        float gain;
        if (player.isCreative()) {
            gain = 1f;
        } else {
            float pr = rs.getDestroyProgress(player, level, rp);
            if (pr <= 0f) {
                Compat.actionBar(player, Component.translatable("ourlet.relay.unbreakable"));
                return true;
            }
            BlockPos ap = end.here().pos();
            float pa = home.getBlockState(ap).getDestroyProgress(player, home, ap);
            // le temps passé à faire céder A, appliqué au bloc d'en face
            float ticksA = pa <= 0f ? 20f : Math.max(1f, (float) Math.ceil(1f / pa));
            gain = pr * ticksA;
        }
        Dig d = DIGS.get(player.getUUID());
        float progress = (d != null && d.pos.equals(rp) ? d.progress : 0f) + gain;
        if (progress < 1f) {
            DIGS.put(player.getUUID(), new Dig(rp, progress));
            level.destroyBlockProgress(-7000 - player.getId(), rp, Math.min(9, (int) (progress * 10f)));
            Compat.actionBar(player, Component.translatable("ourlet.relay.pulling", (int) (progress * 100f)));
            Folds.playAt(level, end.there(), net.minecraft.sounds.SoundEvents.WOOL_HIT, 0.6f, 0.8f + progress * 0.6f);
            return true;
        }
        DIGS.remove(player.getUUID());
        level.destroyBlockProgress(-7000 - player.getId(), rp, -1);
        boolean done;
        busy = true;
        try {
            done = level == home ? player.gameMode.destroyBlock(rp) : Compat.destroyElsewhere(player, level, rp);
        } finally {
            busy = false;
        }
        if (done) {
            pullDrops(level, home, rp, end.here());
            Progress.grant(player, "relay");
        }
        return true;
    }

    /** Ce qui vient de tomber de l'autre côté est ramené par le fil, devant la face A. */
    private static void pullDrops(ServerLevel level, ServerLevel homeLevel, BlockPos from, Anchor home) {
        AABB box = new AABB(from).inflate(1.5);
        Vec3 n = home.normal();
        Vec3 at = home.center().add(n.scale(0.35));
        List<Entity> drops = level.getEntities((Entity) null, box,
            e -> (e instanceof ItemEntity || e instanceof ExperienceOrb) && e.tickCount <= 2);
        for (Entity e : drops) {
            Vec3 to = new Vec3(at.x, at.y - e.getBbHeight() / 2, at.z);
            if (level != homeLevel) {
                Compat.moveAcross(e, homeLevel, to, n.scale(0.12), 0f, 0f);
                continue;
            }
            e.teleportTo(to.x, to.y, to.z);
            e.setOldPosAndRot();
            e.setDeltaMovement(n.scale(0.12));
            Compat.syncMotion(e);
        }
    }
}
