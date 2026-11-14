package com.ourlet.item;

import com.ourlet.fold.Anchor;
import com.ourlet.fold.Fold;
import com.ourlet.fold.Folds;
import com.ourlet.platform.Compat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le Dé à coudre : un pli « de poche ». Accroupi + clic droit : cette face devient la maison.
 * Clic droit n'importe où (même dans une autre dimension) : la face visée est faufilée à la maison
 * pendant 20 secondes, puis le fil de bâti se défait tout seul.
 */
public class ThimbleItem extends Item {

    public static final int DURABILITY = 64;

    public ThimbleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (player == null) return InteractionResult.PASS;
        if (Compat.isClient(level)) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        return pocket(sp, sl, ctx.getClickedPos(), ctx.getClickedFace(), ctx.getItemInHand(), ctx.getHand());
    }

    /** Toute la logique du dé (appelée aussi par le test automatique). */
    public static InteractionResult pocket(ServerPlayer sp, ServerLevel sl, BlockPos pos, Direction face, ItemStack thimble,
                                           InteractionHand hand) {
        var uuid = sp.getUUID();
        BlockState state = sl.getBlockState(pos);
        boolean solid = !state.isAir() && state.isFaceSturdy(sl, pos, face);
        if (sp.isShiftKeyDown()) {
            if (!solid) {
                Compat.actionBar(sp, Component.translatable("ourlet.needle.not_solid"));
                return InteractionResult.FAIL;
            }
            Folds.setHome(uuid, Folds.anchor(sl, pos, face, sp.getDirection()));
            Compat.actionBar(sp, Component.translatable("ourlet.thimble.home_set"));
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        Anchor home = Folds.home(uuid);
        if (home == null) {
            Compat.actionBar(sp, Component.translatable("ourlet.thimble.no_home"));
            return InteractionResult.FAIL;
        }
        if (!Folds.intact(home)) {
            Compat.actionBar(sp, Component.translatable("ourlet.thimble.home_lost"));
            return InteractionResult.FAIL;
        }
        // un seul pli de poche à la fois : l'ancien se défait
        List<Fold> mine = new ArrayList<>();
        for (Fold f : Folds.all()) if (f.temporary() && f.owner.equals(uuid)) mine.add(f);
        for (Fold f : mine) Folds.remove(f, false);
        if (!solid) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.not_solid"));
            return InteractionResult.FAIL;
        }
        if (Folds.find(sl, pos, face) != null) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.already"));
            return InteractionResult.FAIL;
        }
        ServerLevel homeLevel = Folds.level(home);
        if (homeLevel == null || Folds.find(homeLevel, home.pos(), home.face()) != null) {
            Compat.actionBar(sp, Component.translatable("ourlet.thimble.home_busy"));
            return InteractionResult.FAIL;
        }
        Anchor here = Folds.anchor(sl, pos, face, sp.getDirection());
        if (!com.ourlet.OurletConfig.crossDimension && !here.dim().equals(home.dim())) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.no_dimensions"));
            return InteractionResult.FAIL;
        }
        if (here.dim().equals(home.dim()) && here.pos().equals(home.pos())) {
            Compat.actionBar(sp, Component.translatable("ourlet.needle.same_block"));
            return InteractionResult.FAIL;
        }
        Folds.create(uuid, here, home, Folds.pocketTicks());
        if (!sp.isCreative()) Compat.hurtNeedle(thimble, sp, hand);
        Compat.actionBar(sp, Component.translatable("ourlet.thimble.opened", com.ourlet.OurletConfig.pocketSeconds));
        return InteractionResult.SUCCESS;
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
        out.accept(Component.translatable("item.ourlet.thimble.tip1").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.ourlet.thimble.tip2").withStyle(ChatFormatting.GRAY));
        com.ourlet.carnet.CarnetHint.tooltip(out);
    }
}
