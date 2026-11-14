package com.ourlet.platform;

import java.util.Set;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Ce qui change d'une version de Minecraft à l'autre, en un seul endroit.
 * Les blocs //#if sont résolus par tools/port.py pour chaque cible.
 */
public final class Compat {

    private Compat() {}

    public static String dim(Level level) {
        //#if mc26
        //$$ return level.dimension().identifier().toString();
        //#else
        return level.dimension().location().toString();
        //#endif
    }

    public static ServerLevel level(ServerPlayer player) {
        //#if mc26
        //$$ return player.level();
        //#else
        return player.serverLevel();
        //#endif
    }

    public static boolean isClient(LevelAccessor level) {
        return level.isClientSide();
    }

    public static double reach(Player player) {
        //#if mc1201
        //$$ return player.getBlockReach();
        //#else
        return player.blockInteractionRange();
        //#endif
    }

    public static void teleportPlayer(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        //#if mc26
        //$$ p.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false);
        //#else
        p.teleportTo(level, x, y, z, yaw, pitch);
        //#endif
    }

    /** Demande au serveur d'envoyer la nouvelle vitesse aux clients (et au joueur lui-même). */
    public static void syncMotion(Entity e) {
        //#if mc26
        //$$ e.needsSync = true;
        //$$ e.syncVelocity = true;
        //#else
        e.hurtMarked = true;
        e.hasImpulse = true;
        //#endif
    }

    public static Set<String> tags(Entity e) {
        //#if mc26
        //$$ return e.entityTags();
        //#else
        return e.getTags();
        //#endif
    }

    public static void actionBar(ServerPlayer p, Component message) {
        //#if mc26
        //$$ p.sendOverlayMessage(message);
        //#else
        p.displayClientMessage(message, true);
        //#endif
    }

    public static void hurtNeedle(ItemStack stack, ServerPlayer p, InteractionHand hand) {
        //#if mc1201
        //$$ stack.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(hand));
        //#elif mc26
        //$$ stack.hurtAndBreak(1, p, hand);
        //#else
        stack.hurtAndBreak(1, p, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
        //#endif
    }

    public static ParticleOptions dust(int rgb, float size) {
        //#if mc26
        //$$ return new DustParticleOptions(rgb, size);
        //#else
        return new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f,
            (rgb & 255) / 255f), size);
        //#endif
    }

    public static boolean isChunkForced(ServerLevel level, int cx, int cz) {
        //#if mc26
        //$$ return level.getForceLoadedChunks().contains(ChunkPos.pack(cx, cz));
        //#else
        return level.getForcedChunks().contains(ChunkPos.asLong(cx, cz));
        //#endif
    }

    public static boolean entitiesLoaded(ServerLevel level, net.minecraft.core.BlockPos pos) {
        //#if mc26
        //$$ return level.areEntitiesLoaded(ChunkPos.pack(pos));
        //#else
        return level.areEntitiesLoaded(ChunkPos.asLong(pos));
        //#endif
    }

    /** L'objet d'un affichage (item_display), au format de /summon. */
    public static String itemNbt(String id) {
        //#if mc1201
        //$$ return "{id:\"" + id + "\",Count:1b}";
        //#else
        return "{id:\"" + id + "\",count:1}";
        //#endif
    }

    /** Lance une commande serveur, sans rien afficher, dans la dimension de {@code level}. */
    public static void command(ServerLevel level, String command) {
        MinecraftServer server = level.getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "execute in " + dim(level) + " run " + command);
    }

    /** Fait passer une entité (pas un joueur) dans un autre monde, avec position, vitesse et regard. */
    public static Entity moveAcross(Entity e, ServerLevel to, net.minecraft.world.phys.Vec3 pos, net.minecraft.world.phys.Vec3 vel,
                                    float yaw, float pitch) {
        Entity moved;
        //#if mc1201
        //$$ moved = e.changeDimension(to, new net.minecraftforge.common.util.ITeleporter() {
        //$$     @Override
        //$$     public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity ent, ServerLevel dest,
        //$$             java.util.function.Function<ServerLevel, net.minecraft.world.level.portal.PortalInfo> def) {
        //$$         return new net.minecraft.world.level.portal.PortalInfo(pos, vel, yaw, pitch);
        //$$     }
        //$$
        //$$     @Override
        //$$     public Entity placeEntity(Entity ent, ServerLevel current, ServerLevel dest, float y,
        //$$                               java.util.function.Function<Boolean, Entity> reposition) {
        //$$         return reposition.apply(false);
        //$$     }
        //$$
        //$$     @Override
        //$$     public boolean playTeleportSound(ServerPlayer p, ServerLevel s, ServerLevel d) {
        //$$         return false;
        //$$     }
        //$$ });
        //#elif mc26
        //$$ moved = e.teleport(new net.minecraft.world.level.portal.TeleportTransition(to, pos, vel, yaw, pitch,
        //$$     net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        //#else
        moved = e.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(to, pos, vel, yaw, pitch,
            net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
        //#endif
        if (moved != null) {
            moved.setDeltaMovement(vel);
            moved.setOldPosAndRot();
            syncMotion(moved);
        }
        return moved;
    }

    /** Casse un bloc d'un autre monde comme le joueur l'aurait cassé (objets selon l'outil, usure de l'outil). */
    public static boolean destroyElsewhere(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return false;
        ItemStack tool = player.getMainHandItem();
        boolean creative = player.isCreative();
        var be = level.getBlockEntity(pos);
        if (!creative && player.hasCorrectToolForDrops(state)) {
            net.minecraft.world.level.block.Block.dropResources(state, level, pos, be, player, tool);
        }
        boolean done = level.destroyBlock(pos, false, player);
        if (done && !creative) tool.mineBlock(level, state, pos, player);
        return done;
    }
}
