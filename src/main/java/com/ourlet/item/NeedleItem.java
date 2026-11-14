package com.ourlet.item;

import com.ourlet.fold.Anchor;
import com.ourlet.fold.Fold;
import com.ourlet.fold.Folds;
import com.ourlet.platform.Compat;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'Aiguille Spatiale. Clic droit sur une face : premier point. Clic droit sur une autre face :
 * les deux faces sont cousues. Accroupi + clic droit sur une couture : on la découd.
 */
public class NeedleItem extends Item {

    public static final int DURABILITY = 48;

    public NeedleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (player == null) return InteractionResult.PASS;
        if (Compat.isClient(level)) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        return sew(sp, sl, ctx.getClickedPos(), ctx.getClickedFace(), ctx.getItemInHand(), ctx.getHand());
    }

    /** Toute la logique de l'aiguille (appelée aussi par le test automatique). */
    public static InteractionResult sew(ServerPlayer sp, ServerLevel sl, BlockPos pos, Direction face, ItemStack needle,
                                        net.minecraft.world.InteractionHand hand) {
        var uuid = sp.getUUID();
        Folds.End existing = Folds.find(sl, pos, face);
        Folds.Pending pending = Folds.pending(uuid);

        if (sp.isShiftKeyDown()) {
            if (existing != null) {
                if (!Folds.mayEdit(sp, existing.fold())) {
                    Compat.actionBar(sp, Component.translatable("ourlet.protected", Folds.ownerName(existing.fold())));
                    return InteractionResult.FAIL;
                }
                Folds.remove(existing.fold(), false);
                Compat.actionBar(sp, Component.translatable("ourlet.needle.cut"));
                return InteractionResult.SUCCESS;
            }
            if (pending != null) {
                Folds.setPending(uuid, null);
                Compat.actionBar(sp, Component.translatable("ourlet.needle.cancel"));
                sl.playSound(null, pos, SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6f, 1.4f);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        if (existing != null) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.already"));
            return InteractionResult.FAIL;
        }
        BlockState state = sl.getBlockState(pos);
        if (state.isAir() || !state.isFaceSturdy(sl, pos, face)) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.not_solid"));
            return InteractionResult.FAIL;
        }
        Anchor here = Folds.anchor(sl, pos, face, sp.getDirection());
        // une bobine dans l'autre main : grande couture 3×3
        boolean spool = sp.getItemInHand(hand == net.minecraft.world.InteractionHand.MAIN_HAND
            ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND).getItem()
            == com.ourlet.registry.OurletItems.SPOOL.get();

        if (pending == null || !Folds.intact(pending.anchor())) {
            int size = spool ? 3 : 1;
            if (size > 1 && !Folds.sewable(sl, here, size)) {
                Compat.actionBar(sp, Component.translatable("ourlet.needle.patch_space"));
                return InteractionResult.FAIL;
            }
            Compat.actionBar(sp, Component.translatable(size > 1 ? "ourlet.needle.first_patch" : "ourlet.needle.first"));
            Folds.setPending(uuid, new Folds.Pending(here, sl.getGameTime(), size));
            sl.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8f, size > 1 ? 1.1f : 1.5f);
            return InteractionResult.SUCCESS;
        }
        Anchor first = pending.anchor();
        int size = pending.size();
        if (first.dim().equals(here.dim()) && overlaps(first, here, size)) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.same_block"));
            return InteractionResult.FAIL;
        }
        ServerLevel firstLevel = Folds.level(first);
        if (firstLevel == null || !Folds.sewable(firstLevel, first, size)) {
            Folds.setPending(uuid, new Folds.Pending(here, sl.getGameTime(), spool ? 3 : 1));
            Compat.actionBar(sp, Component.translatable("ourlet.needle.first"));
            return InteractionResult.SUCCESS;
        }
        if (!com.ourlet.OurletConfig.crossDimension && !first.dim().equals(here.dim())) {
            Folds.setPending(uuid, new Folds.Pending(here, sl.getGameTime(), spool ? 3 : 1));
            Compat.actionBar(sp, Component.translatable("ourlet.needle.no_dimensions"));
            return InteractionResult.SUCCESS;
        }
        if (size > 1 && !Folds.sewable(sl, here, size)) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.patch_space"));
            return InteractionResult.FAIL;
        }
        if (!sp.isCreative() && Folds.owned(uuid) >= com.ourlet.OurletConfig.maxFolds) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.too_many", com.ourlet.OurletConfig.maxFolds));
            return InteractionResult.FAIL;
        }
        Fold f = Folds.create(uuid, first, here, -1, size);
        Folds.setPending(uuid, null);
        if (!sp.isCreative()) for (int i = 0; i < (size > 1 ? 3 : 1) && !needle.isEmpty(); i++) Compat.hurtNeedle(needle, sp, hand);
        Compat.actionBar(sp, f.crossDimension() ? Component.translatable("ourlet.needle.sewn_dimensions")
            : Component.translatable("ourlet.needle.sewn", f.span()));
        return InteractionResult.SUCCESS;
    }

    /** Deux coutures du même plan qui se chevaucheraient. */
    private static boolean overlaps(Anchor a, Anchor b, int size) {
        for (var c : a.cells(size)) for (var d : b.cells(size)) if (c.equals(d)) return true;
        return false;
    }

    //#if mc26
    //$$ @Override
    //$$ public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display,
    //$$                             java.util.function.Consumer<Component> out, TooltipFlag flag) {
    //$$     tooltip(out::accept);
    //$$ }
    //#elif mc1201
    //$$ @Override
    //$$ public void appendHoverText(ItemStack stack, Level level, List<Component> out, TooltipFlag flag) {
    //$$     tooltip(out::add);
    //$$ }
    //#else
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        tooltip(out::add);
    }
    //#endif

    private static void tooltip(java.util.function.Consumer<Component> out) {
        out.accept(Component.translatable("item.ourlet.spatial_needle.tip1").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.ourlet.spatial_needle.tip2").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.ourlet.spatial_needle.tip3").withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.translatable("item.ourlet.spatial_needle.tip4").withStyle(ChatFormatting.DARK_GRAY));
        com.ourlet.carnet.CarnetHint.tooltip(out);
    }
}
