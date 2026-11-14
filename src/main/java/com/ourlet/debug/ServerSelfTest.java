package com.ourlet.debug;

import com.ourlet.Ourlet;
import com.ourlet.fold.Anchor;
import com.ourlet.fold.Fold;
import com.ourlet.fold.Folds;
import com.ourlet.fold.Markers;
import com.ourlet.item.NeedleItem;
import com.ourlet.platform.Compat;
import com.ourlet.registry.OurletItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Test automatique en jeu, pour l'intégration continue uniquement (-Dourlet.selftest=true).
 *
 * Au démarrage du serveur : un objet qui tombe, une flèche et un piston traversent chacun un pli
 * (à 1 500 blocs de distance), puis « [SELFTEST] RESULT: OK|FAIL n » et « [SELFTEST] READY ».
 * Quand le client de test se connecte : un atelier pour les captures, un pli vers une « mine »
 * à 3 000 blocs, et la vérification qu'un minerai y est cassé à travers le pli (RELAY).
 */
public final class ServerSelfTest {

    private ServerSelfTest() {}

    private static final boolean ENABLED = Boolean.getBoolean("ourlet.selftest");
    private static final UUID TESTER = UUID.fromString("00000000-0000-4000-8000-0000000c0de5");
    private static final int Y = 200;

    private record Check(String what, long at, BooleanSupplier ok) {}

    private static final List<Check> CHECKS = new ArrayList<>();
    private static final List<Runnable> LATER = new ArrayList<>();
    private static long laterAt = -1;
    private static int failures;
    private static boolean startupDone, relayWatch;
    private static long relayDeadline;
    private static BlockPos ore, wallA;
    private static int sx, sz;

    public static boolean enabled() {
        return ENABLED;
    }

    private static void log(boolean ok, String what) {
        if (ok) Ourlet.LOGGER.info("[SELFTEST] ok : {}", what);
        else {
            failures++;
            Ourlet.LOGGER.error("[SELFTEST] ÉCHEC : {}", what);
        }
    }

    private static void set(ServerLevel level, int x, int y, int z, Block b) {
        level.setBlock(new BlockPos(x, y, z), b.defaultBlockState(), 3);
    }

    private static Anchor anchor(ServerLevel level, BlockPos pos, Direction face, Direction facing) {
        return new Anchor(pos, face, Anchor.upFor(face, facing), Folds.blockId(level.getBlockState(pos)));
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!ENABLED) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        var src = server.createCommandSourceStack().withSuppressedOutput();
        for (String c : new String[]{"time set 5000", "weather clear 999999", "gamerule doDaylightCycle false",
            "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule advance_time false",
            "gamerule advance_weather false", "gamerule spawn_mobs false"}) {
            try {
                server.getCommands().performPrefixedCommand(src, c);
            } catch (RuntimeException ignored) {
                // nom de règle absent dans cette version
            }
        }
        //#if mc26
        //$$ BlockPos spawn = level.getRespawnData().pos();
        //#else
        BlockPos spawn = level.getSharedSpawnPos();
        //#endif
        sx = spawn.getX();
        sz = spawn.getZ();
        long t = level.getGameTime();

        // 1. Un objet tombe sur un sol cousu et ressort d'un mur, vers l'est.
        BlockPos a1 = new BlockPos(sx + 20, Y, sz), b1 = new BlockPos(sx + 1520, Y, sz);
        set(level, a1.getX(), Y, sz, Blocks.STONE);
        set(level, b1.getX(), Y, sz, Blocks.STONE);
        Fold f1 = Folds.create(level, TESTER, anchor(level, a1, Direction.UP, Direction.NORTH),
            anchor(level, b1, Direction.EAST, Direction.NORTH));

        // 2. Une flèche file vers un mur cousu et ressort d'un sol, vers le haut.
        BlockPos a2 = new BlockPos(sx + 30, Y, sz), b2 = new BlockPos(sx + 1530, Y + 5, sz);
        set(level, a2.getX(), Y, sz, Blocks.STONE);
        set(level, b2.getX(), b2.getY(), sz, Blocks.STONE);
        Fold f2 = Folds.create(level, TESTER, anchor(level, a2, Direction.WEST, Direction.NORTH),
            anchor(level, b2, Direction.UP, Direction.NORTH));

        // 3. Un piston pousse un bloc de pierre taillée contre une couture : il ressort à 1 500 blocs.
        BlockPos piston = new BlockPos(sx + 40, Y, sz), a3 = piston.east(2), b3 = new BlockPos(sx + 1540, Y, sz);
        level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 3);
        set(level, piston.getX() + 1, Y, sz, Blocks.COBBLESTONE);
        set(level, a3.getX(), Y, sz, Blocks.STONE);
        set(level, b3.getX(), Y, sz, Blocks.STONE);
        Fold f3 = Folds.create(level, TESTER, anchor(level, a3, Direction.WEST, Direction.NORTH),
            anchor(level, b3, Direction.NORTH, Direction.NORTH));
        BlockPos out3 = b3.north();

        log(Folds.all().size() == 3, "trois plis cousus");
        log(Fold.mapVector(f1.a, f1.b, new Vec3(0, -1, 0)).distanceTo(new Vec3(1, 0, 0)) < 1e-6,
            "une chute dans le sol ressort à l'horizontale du mur");

        later(level, t + 10, () -> {
            ItemEntity item = new ItemEntity(level, a1.getX() + 0.5, Y + 1.6, sz + 0.5, new ItemStack(Items.DIAMOND));
            item.setDeltaMovement(0, -0.25, 0);
            item.setPickUpDelay(100);
            level.addFreshEntity(item);
            Entity arrow = makeArrow(level, a2.getX() - 6.0, Y + 0.5, sz + 0.5);
            arrow.setDeltaMovement(1.8, 0, 0);
            boolean added = level.addFreshEntity(arrow);
            Ourlet.LOGGER.info("[SELFTEST] flèche lancée ({})", added);
            set(level, piston.getX(), Y + 1, sz, Blocks.REDSTONE_BLOCK);
            CHECKS.add(new Check("l'objet ressort à 1 500 blocs, poussé vers l'est", t + 80, () -> {
                var ex = com.ourlet.fold.Transit.TEST_EXITS.get(item.getId());
                Ourlet.LOGGER.info("[SELFTEST] sortie de l'objet : {}", ex);
                return ex != null && ex.pos().x > b1.getX() + 1 && ex.pos().x < b1.getX() + 1.5 && ex.velocity().x > 0.2
                    && Math.abs(ex.velocity().y) < 0.05;
            }));
            CHECKS.add(new Check("la flèche ressort du sol, vers le haut", t + 80, () -> {
                var ex = com.ourlet.fold.Transit.TEST_EXITS.get(arrow.getId());
                Ourlet.LOGGER.info("[SELFTEST] sortie de la flèche : {} (maintenant en {})", ex, arrow.position());
                return ex != null && Math.abs(ex.pos().x - b2.getX() - 0.5) < 1 && ex.pos().y >= b2.getY() + 1
                    && ex.velocity().y > 1.5;
            }));
            CHECKS.add(new Check("le piston a poussé la pierre taillée à travers le pli", t + 80, () ->
                level.getBlockState(out3).is(Blocks.COBBLESTONE)));
            CHECKS.add(new Check("les coutures sont visibles (6 affichages)", t + 70, () -> {
                int n = Markers.count(level, f1) + Markers.count(level, f2) + Markers.count(level, f3);
                Ourlet.LOGGER.info("[SELFTEST] affichages : {}", n);
                return n == 6;
            }));
            CHECKS.add(new Check("les plis sont sauvegardés", t + 80, () -> savedLines(server) >= 3));
        });
    }

    private static Entity makeArrow(ServerLevel level, double x, double y, double z) {
        //#if mc26
        //$$ return new net.minecraft.world.entity.projectile.arrow.Arrow(level, x, y, z, new ItemStack(Items.ARROW), null);
        //#elif mc1201
        //$$ return new net.minecraft.world.entity.projectile.Arrow(level, x, y, z);
        //#else
        return new net.minecraft.world.entity.projectile.Arrow(level, x, y, z, new ItemStack(Items.ARROW), null);
        //#endif
    }

    private static int savedLines(MinecraftServer server) {
        try {
            var p = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve("ourlet_folds.txt");
            return (int) java.nio.file.Files.readAllLines(p).stream().filter(l -> !l.startsWith("#") && !l.isBlank()).count();
        } catch (java.io.IOException e) {
            return 0;
        }
    }

    private static void later(ServerLevel level, long at, Runnable r) {
        LATER.add(r);
        laterAt = at;
    }

    /** Appelé à chaque tick serveur (depuis ServerEvents). */
    public static void tick(MinecraftServer server) {
        if (!ENABLED) return;
        long now = server.overworld().getGameTime();
        if (laterAt >= 0 && now >= laterAt) {
            laterAt = -1;
            List<Runnable> rs = new ArrayList<>(LATER);
            LATER.clear();
            rs.forEach(Runnable::run);
        }
        if (!CHECKS.isEmpty()) {
            CHECKS.removeIf(c -> {
                if (now < c.at) return false;
                try {
                    log(c.ok.getAsBoolean(), c.what);
                } catch (RuntimeException e) {
                    log(false, c.what + " (" + e + ")");
                }
                return true;
            });
            if (CHECKS.isEmpty() && !startupDone) {
                startupDone = true;
                log(com.ourlet.fold.Transit.passages() >= 2, "passages comptés : " + com.ourlet.fold.Transit.passages());
                Ourlet.LOGGER.info("[SELFTEST] RESULT: {}", failures == 0 ? "OK" : "FAIL " + failures);
                Ourlet.LOGGER.info("[SELFTEST] READY");
            }
        }
        if (relayWatch) {
            ServerLevel level = server.overworld();
            if (level.getBlockState(ore).isAir()) {
                relayWatch = false;
                boolean wallKept = !level.getBlockState(wallA).isAir();
                Ourlet.LOGGER.info("[SELFTEST] RELAY: {}", wallKept ? "OK" : "FAIL (le mur cousu a cassé)");
            } else if (now > relayDeadline) {
                relayWatch = false;
                Ourlet.LOGGER.error("[SELFTEST] RELAY: FAIL (minerai intact)");
            }
        }
    }

    /** L'atelier du joueur de test : un mur de laine cousu à une mine lointaine, et un sol cousu à un plafond. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer sp)) return;
        ServerLevel level = Compat.level(sp);
        MinecraftServer server = level.getServer();
        //#if mc26
        //$$ server.getPlayerList().op(new net.minecraft.server.players.NameAndId(sp.getGameProfile()));
        //#else
        server.getPlayerList().op(sp.getGameProfile());
        //#endif
        sp.setGameMode(GameType.CREATIVE);
        int z0 = sz + 8;
        // sol de bouleau et murs de laine blanche
        for (int x = sx - 3; x <= sx + 3; x++) {
            for (int z = z0 - 2; z <= z0 + 4; z++) {
                set(level, x, Y - 1, z, Blocks.BIRCH_PLANKS);
                for (int y = Y; y <= Y + 3; y++) set(level, x, y, z, Blocks.AIR);
            }
            for (int y = Y; y <= Y + 2; y++) set(level, x, y, z0 + 4, Blocks.WHITE_WOOL);
        }
        for (int y = Y; y <= Y + 2; y++) {
            set(level, sx - 3, y, z0 + 3, Blocks.PINK_WOOL);
            set(level, sx + 3, y, z0 + 3, Blocks.LIGHT_BLUE_WOOL);
        }
        wallA = new BlockPos(sx, Y + 1, z0 + 4);
        // la mine, à 3 000 blocs : une salle de pierre, un minerai de diamant face au bout cousu
        int mx = sx + 3000;
        for (int x = mx - 3; x <= mx + 3; x++) {
            for (int z = z0 + 3; z <= z0 + 10; z++) {
                set(level, x, Y - 1, z, Blocks.STONE);
                set(level, x, Y + 3, z, Blocks.DEEPSLATE);
                for (int y = Y; y <= Y + 2; y++) set(level, x, y, z, Blocks.AIR);
            }
            for (int y = Y; y <= Y + 2; y++) set(level, x, y, z0 + 4, Blocks.STONE);
        }
        BlockPos wallB = new BlockPos(mx, Y + 1, z0 + 4);
        ore = new BlockPos(mx, Y + 1, z0 + 6);
        set(level, ore.getX(), ore.getY(), ore.getZ(), Blocks.DIAMOND_ORE);
        set(level, mx + 2, Y, z0 + 8, Blocks.GLOWSTONE);

        // les plis sont cousus avec la vraie aiguille, par le joueur
        ItemStack needle = new ItemStack(OurletItems.NEEDLE.get());
        sp.getInventory().setItem(0, needle);
        sp.getInventory().setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
        sp.getInventory().setItem(2, new ItemStack(Items.TORCH, 16));
        //#if mc26
        //$$ sp.getInventory().setSelectedSlot(0);
        //#else
        sp.getInventory().selected = 0;
        //#endif
        sp.setShiftKeyDown(false);
        var r1 = NeedleItem.sew(sp, level, wallA, Direction.NORTH, needle, InteractionHand.MAIN_HAND);
        var r2 = NeedleItem.sew(sp, level, wallB, Direction.SOUTH, needle, InteractionHand.MAIN_HAND);
        log(Folds.find(level, wallA, Direction.NORTH) != null, "l'aiguille coud le mur de l'atelier à la mine (" + r1 + ", " + r2 + ")");
        // un second pli, pour les captures : le sol de l'atelier vers le plafond de la mine
        NeedleItem.sew(sp, level, new BlockPos(sx + 2, Y - 1, z0 + 1), Direction.UP, needle, InteractionHand.MAIN_HAND);
        NeedleItem.sew(sp, level, new BlockPos(mx - 2, Y + 3, z0 + 8), Direction.DOWN, needle, InteractionHand.MAIN_HAND);

        Compat.teleportPlayer(sp, level, sx + 0.5, Y, z0 + 1.5, 0f, 3f);
        relayWatch = true;
        relayDeadline = level.getGameTime() + 20 * 120;
        Ourlet.LOGGER.info("[SELFTEST] atelier prêt pour {} (mur cousu {}, minerai {})", sp.getName().getString(),
            wallA.toShortString(), ore.toShortString());
    }
}
