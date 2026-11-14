package com.ourlet.event;

import com.mojang.brigadier.Command;
import com.ourlet.fold.Anchor;
import com.ourlet.fold.Fold;
import com.ourlet.fold.Folds;
import com.ourlet.fold.Markers;
import com.ourlet.fold.Relay;
import com.ourlet.fold.Transit;
import com.ourlet.item.NeedleItem;
import com.ourlet.platform.Compat;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
//#if mc1201
//$$ import net.minecraftforge.event.TickEvent;
//#elif neoforge
//$$ import net.neoforged.neoforge.event.tick.ServerTickEvent;
//#else
import net.minecraftforge.event.TickEvent;
//#endif
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Tous les branchements sur le jeu, côté serveur. */
public final class ServerEvents {

    private ServerEvents() {}

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        Folds.load(event.getServer());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        Folds.unload();
    }

    //#if mc1201
    //$$ @SubscribeEvent
    //$$ public static void onTick(TickEvent.ServerTickEvent event) {
    //$$     if (event.phase == TickEvent.Phase.END) tick();
    //$$ }
    //#elif neoforge
    //$$ @SubscribeEvent
    //$$ public static void onTick(ServerTickEvent.Post event) {
    //$$     tick();
    //$$ }
    //#else
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent.Post event) {
        tick();
    }
    //#endif

    private static void tick() {
        MinecraftServer server = Folds.server();
        if (server == null) return;
        Folds.tickGlobal();
        for (ServerLevel level : server.getAllLevels()) {
            Transit.trackPlayers(level);
            Folds.tick(level);
        }
        com.ourlet.debug.ServerSelfTest.tick(server);
    }

    /** Clic droit sur une face cousue (sans l'aiguille) : l'action est faite de l'autre côté. */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || Relay.busy()) return;
        var held = player.getItemInHand(event.getHand()).getItem();
        if (held instanceof NeedleItem || held instanceof com.ourlet.item.ThimbleItem) return;
        BlockHitResult hit = event.getHitVec();
        Folds.End end = Folds.find(Compat.level(player), hit.getBlockPos(), hit.getDirection());
        if (end == null) return;
        if (held instanceof net.minecraft.world.item.ShearsItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            snip(player, end);
            return;
        }
        if (!end.open()) return; // pli fermé ou à sens unique dans l'autre sens : le bloc redevient un bloc
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Relay.use(player, event.getHand(), end, hit.getLocation());
    }

    /** Ciseaux : ouvert → sens unique (on entre par cette face) → fermé → ouvert. */
    private static void snip(ServerPlayer player, Folds.End end) {
        Fold f = end.fold();
        boolean isA = end.here() == f.a;
        int next = switch (f.mode()) {
            case Fold.OPEN -> isA ? Fold.ONLY_A : Fold.ONLY_B;
            case Fold.ONLY_A, Fold.ONLY_B -> Fold.CLOSED;
            default -> Fold.OPEN;
        };
        Folds.setMode(f, next);
        String key = switch (next) {
            case Fold.OPEN -> "ourlet.shears.open";
            case Fold.CLOSED -> "ourlet.shears.closed";
            default -> "ourlet.shears.one_way";
        };
        Compat.actionBar(player, Component.translatable(key));
        com.ourlet.fold.Effects.snip(Compat.level(player), end.here(), f.color);
    }

    /** Casser un bloc cousu par sa face cousue : c'est le bloc d'en face qui cède. */
    //#if mc26
    //$$ @SubscribeEvent
    //$$ public static void onBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
    //#else
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
    //#endif
        if (!(event.getPlayer() instanceof ServerPlayer player) || Relay.busy()) return;
        if (Folds.all().isEmpty()) return;
        BlockHitResult hit = Relay.pick(player);
        if (hit == null || !hit.getBlockPos().equals(event.getPos())) return;
        Folds.End end = Folds.find(Compat.level(player), event.getPos(), hit.getDirection());
        if (end == null || !end.open()) return;
        if (Relay.dig(player, end, hit.getLocation())) event.setCanceled(true);
    }

    /**
     * Un piston pousse une rangée de blocs contre une face cousue : le bloc de tête passe
     * par le pli et ressort devant l'autre face. Le reste de la rangée avance normalement.
     */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (event.getPistonMoveType() != PistonEvent.PistonMoveType.EXTEND) return;
        if (!(event.getLevel() instanceof ServerLevel level) || Folds.all().isEmpty()) return;
        Direction dir = event.getDirection();
        BlockPos p = event.getPos().relative(dir);
        BlockPos last = null;
        for (int i = 0; i < 13; i++) {
            BlockState s = level.getBlockState(p);
            Folds.End end = Folds.find(level, p, dir.getOpposite());
            if (end != null) {
                if (last == null || !end.open()) return; // rien à faire passer, ou pli fermé
                BlockState moved = level.getBlockState(last);
                //#if mc26
                //$$ if (moved.hasBlockEntity() || moved.getPistonPushReaction() != PushReaction.PUSH_PULL) return;
                //#else
                if (moved.hasBlockEntity() || moved.getPistonPushReaction() != PushReaction.NORMAL) return;
                //#endif
                Anchor there = end.there();
                ServerLevel thereLevel = Folds.level(there);
                BlockPos out = there.pos().relative(there.face());
                if (thereLevel == null || !thereLevel.getBlockState(out).canBeReplaced()) {
                    event.setCanceled(true); // la sortie est bouchée : le piston bute
                    return;
                }
                level.setBlock(last, Blocks.AIR.defaultBlockState(), 3);
                thereLevel.setBlock(out, moved, 3);
                com.ourlet.fold.Effects.transit(end.here(), there, end.fold().color, 1.0);
                return;
            }
            if (s.isAir() || s.canBeReplaced()) return;
            last = p;
            p = p.relative(dir);
        }
    }

    /** Une couture dont le pli a disparu ne doit pas revenir quand son tronçon se recharge. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!Compat.isClient(event.getLevel()) && Markers.isOrphan(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Folds.setPending(event.getEntity().getUUID(), null);
        Relay.forget(event.getEntity().getUUID());
    }

    private static String where(Anchor a) {
        String d = a.dim();
        return a.pos().toShortString() + (d.equals("minecraft:overworld") ? "" : " (" + d.replace("minecraft:", "") + ")");
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ourlet").then(Commands.literal("list").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int n = 0;
            for (Fold f : Folds.all()) {
                if (!f.owner.equals(player.getUUID()) || f.temporary()) continue;
                n++;
                Component line = Component.translatable("ourlet.command.line", f.id, where(f.a), where(f.b),
                    Component.translatable("ourlet.thread." + f.color), Component.translatable("ourlet.mode." + f.mode()));
                ctx.getSource().sendSuccess(() -> line, false);
            }
            final int count = n;
            ctx.getSource().sendSuccess(() -> Component.translatable("ourlet.command.count", count, Folds.MAX_PER_PLAYER), false);
            return Command.SINGLE_SUCCESS;
        })));
    }
}
