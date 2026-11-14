package com.ourlet.fold;

import com.ourlet.OurletConfig;
import com.ourlet.platform.Compat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le regard à travers la couture : quand un joueur vise une face cousue, la barre d'action dit
 * où mène le pli (dimension, position, distance), dans quel état il est, et quel bloc on
 * toucherait de l'autre côté. Calculé côté serveur, donc aussi pour les clients sans le mod… ou presque.
 */
public final class Gaze {

    private Gaze() {}

    /** Jusqu'à quel tick ne rien afficher (un autre message d'Ourlet vient d'être montré). */
    private static final Map<UUID, Long> QUIET = new HashMap<>();
    /** Le joueur visait-il une couture au dernier passage ? (pour effacer la ligne quand il détourne les yeux) */
    private static final Map<UUID, Boolean> SHOWING = new HashMap<>();

    public static void hush(ServerPlayer p) {
        if (Folds.server() == null) return;
        QUIET.put(p.getUUID(), Folds.server().overworld().getGameTime() + 50);
    }

    public static void reset() {
        QUIET.clear();
        SHOWING.clear();
    }

    public static void tick(MinecraftServer server) {
        if (!OurletConfig.gaze || Folds.all().isEmpty()) return;
        long now = server.overworld().getGameTime();
        if (now % 4 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Long quiet = QUIET.get(p.getUUID());
            if (quiet != null && quiet > now) continue;
            Component line = describe(p);
            boolean was = SHOWING.getOrDefault(p.getUUID(), false);
            if (line != null) {
                Compat.actionBarRaw(p, line);
                SHOWING.put(p.getUUID(), true);
            } else if (was) {
                Compat.actionBarRaw(p, Component.literal(""));
                SHOWING.put(p.getUUID(), false);
            }
        }
    }

    public static String dimName(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "ourlet.dim.overworld";
            case "minecraft:the_nether" -> "ourlet.dim.nether";
            case "minecraft:the_end" -> "ourlet.dim.end";
            default -> dim;
        };
    }

    /** La ligne à afficher pour ce joueur, ou null s'il ne vise pas une couture. */
    public static Component describe(ServerPlayer p) {
        BlockHitResult hit = Relay.pick(p);
        if (hit == null) return null;
        ServerLevel level = Compat.level(p);
        Folds.End end = Folds.find(level, hit.getBlockPos(), hit.getDirection());
        if (end == null) return null;
        Fold f = end.fold();
        Anchor there = end.there();
        int rgb = Effects.rgb(f.color());
        MutableComponent line = Component.literal("⇄ ").withStyle(s -> s.withColor(rgb));
        if (f.name() != null) line.append(Component.literal("« " + f.name() + " » ").withStyle(s -> s.withColor(rgb)));
        line.append(Component.translatable(dimName(there.dim())).withStyle(ChatFormatting.WHITE));
        line.append(Component.literal(" " + there.pos().getX() + " " + there.pos().getY() + " " + there.pos().getZ())
            .withStyle(ChatFormatting.GRAY));
        if (!f.crossDimension()) {
            line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.translatable("ourlet.gaze.blocks", f.span()).withStyle(ChatFormatting.GRAY));
        }
        line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
        String state;
        if (f.powered()) state = "ourlet.gaze.powered";
        else if (f.mode() == Fold.CLOSED) state = "ourlet.gaze.closed";
        else if (f.mode() == Fold.OPEN) state = "ourlet.gaze.open";
        else state = f.canEnter(end.here()) ? "ourlet.gaze.in" : "ourlet.gaze.out";
        line.append(Component.translatable(state).withStyle(end.open() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (f.tension() != Fold.NORMAL) {
            line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.translatable(f.tension() == Fold.ELASTIC ? "ourlet.gaze.elastic" : "ourlet.gaze.damped")
                .withStyle(f.tension() == Fold.ELASTIC ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
        if (f.size > 1) line.append(Component.literal(" · 3×3").withStyle(ChatFormatting.GRAY));
        if (f.temporary()) {
            long left = Math.max(0, (f.expires() - Folds.now()) / 20);
            line.append(Component.literal(" ⌛" + left + "s").withStyle(ChatFormatting.YELLOW));
        }
        if (end.open() && OurletConfig.relay) {
            BlockHitResult beyond = Relay.beyond(p, end, hit.getLocation());
            ServerLevel thereLevel = Folds.level(there);
            if (beyond != null && thereLevel != null) {
                var state2 = thereLevel.getBlockState(beyond.getBlockPos());
                if (!state2.isAir()) {
                    line.append(Component.literal(" ▸ ").withStyle(ChatFormatting.DARK_GRAY));
                    line.append(state2.getBlock().getName().copy().withStyle(ChatFormatting.WHITE));
                }
            }
        }
        return line;
    }
}
