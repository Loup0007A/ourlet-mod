package com.ourlet.debug;

import com.ourlet.Ourlet;
import com.ourlet.fold.Anchor;
import com.ourlet.fold.Fold;
import com.ourlet.fold.Folds;
import com.ourlet.fold.Markers;
import com.ourlet.item.NeedleItem;
import com.ourlet.item.ThimbleItem;
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
    private static boolean startupDone, relayWatch, pocketWatch;
    private static long relayDeadline, pocketDeadline;
    private static Fold shortPocket;
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
        return Folds.anchor(level, pos, face, facing);
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
        Fold f1 = Folds.create(TESTER, anchor(level, a1, Direction.UP, Direction.NORTH),
            anchor(level, b1, Direction.EAST, Direction.NORTH));

        // 2. Une flèche file vers un mur cousu et ressort d'un sol, vers le haut.
        BlockPos a2 = new BlockPos(sx + 30, Y, sz), b2 = new BlockPos(sx + 1530, Y + 5, sz);
        set(level, a2.getX(), Y, sz, Blocks.STONE);
        set(level, b2.getX(), b2.getY(), sz, Blocks.STONE);
        Fold f2 = Folds.create(TESTER, anchor(level, a2, Direction.WEST, Direction.NORTH),
            anchor(level, b2, Direction.UP, Direction.NORTH));

        // 3. Un piston pousse un bloc de pierre taillée contre une couture : il ressort à 1 500 blocs.
        BlockPos piston = new BlockPos(sx + 40, Y, sz), a3 = piston.east(2), b3 = new BlockPos(sx + 1540, Y, sz);
        level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 3);
        set(level, piston.getX() + 1, Y, sz, Blocks.COBBLESTONE);
        set(level, a3.getX(), Y, sz, Blocks.STONE);
        set(level, b3.getX(), Y, sz, Blocks.STONE);
        Fold f3 = Folds.create(TESTER, anchor(level, a3, Direction.WEST, Direction.NORTH),
            anchor(level, b3, Direction.NORTH, Direction.NORTH));
        BlockPos out3 = b3.north();

        // 4. Entre deux dimensions : un objet tombe dans un sol cousu et ressort d'un mur du Nether.
        ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        BlockPos a4 = new BlockPos(sx + 50, Y, sz), b4 = new BlockPos(8, 100, 8);
        set(level, a4.getX(), Y, sz, Blocks.STONE);
        Fold f4 = null;
        if (nether != null) {
            for (int dx = -2; dx <= 4; dx++) for (int dy = -1; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++)
                set(nether, b4.getX() + dx, b4.getY() + dy, b4.getZ() + dz, dy == -1 ? Blocks.NETHERRACK : Blocks.AIR);
            set(nether, b4.getX(), b4.getY(), b4.getZ(), Blocks.NETHER_BRICKS);
            f4 = Folds.create(TESTER, anchor(level, a4, Direction.UP, Direction.NORTH),
                anchor(nether, b4, Direction.EAST, Direction.NORTH));
        }
        // 5. Un pli fermé par la redstone : l'objet reste posé dessus.
        BlockPos a5 = new BlockPos(sx + 60, Y, sz), b5 = new BlockPos(sx + 1560, Y, sz);
        set(level, a5.getX(), Y, sz, Blocks.STONE);
        set(level, b5.getX(), Y, sz, Blocks.STONE);
        set(level, a5.getX(), Y, sz + 1, Blocks.REDSTONE_BLOCK);
        Fold f5 = Folds.create(TESTER, anchor(level, a5, Direction.UP, Direction.NORTH),
            anchor(level, b5, Direction.EAST, Direction.NORTH));
        // 6. Un pli à sens unique (on n'entre que par B) : l'objet posé sur A reste là.
        BlockPos a6 = new BlockPos(sx + 70, Y, sz), b6 = new BlockPos(sx + 1570, Y, sz);
        set(level, a6.getX(), Y, sz, Blocks.STONE);
        set(level, b6.getX(), Y, sz, Blocks.STONE);
        Fold f6 = Folds.create(TESTER, anchor(level, a6, Direction.UP, Direction.NORTH),
            anchor(level, b6, Direction.EAST, Direction.NORTH));
        Folds.setMode(f6, Fold.ONLY_B);
        final Fold f4f = f4;

        // 7. Une grande couture 3×3 : un sol vers un mur ; un objet tombé sur un coin ressort par le coin jumeau.
        BlockPos a7 = new BlockPos(sx + 80, Y, sz), b7 = new BlockPos(sx + 1580, Y + 1, sz);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                set(level, a7.getX() + i, Y, sz + j, Blocks.STONE);
                set(level, b7.getX(), Y + 1 + i, sz + j, Blocks.STONE);
            }
        }
        Fold f7 = Folds.create(TESTER, anchor(level, a7, Direction.UP, Direction.NORTH),
            anchor(level, b7, Direction.EAST, Direction.NORTH), -1, 3);
        // 8. Un fil élastique : on ressort plus vite.
        BlockPos a8 = new BlockPos(sx + 90, Y, sz), b8 = new BlockPos(sx + 1590, Y, sz);
        set(level, a8.getX(), Y, sz, Blocks.STONE);
        set(level, b8.getX(), Y, sz, Blocks.STONE);
        Fold f8 = Folds.create(TESTER, anchor(level, a8, Direction.UP, Direction.NORTH),
            anchor(level, b8, Direction.EAST, Direction.NORTH));
        Folds.setTension(f8, Fold.ELASTIC);
        // 9. Un fil ciré au plafond, cousu au sol juste en dessous : une chute sans fin, tout en douceur.
        BlockPos a9 = new BlockPos(sx + 100, Y, sz), b9 = new BlockPos(sx + 100, Y + 5, sz);
        set(level, a9.getX(), Y, sz, Blocks.STONE);
        set(level, b9.getX(), b9.getY(), sz, Blocks.STONE);
        Fold f9 = Folds.create(TESTER, anchor(level, a9, Direction.UP, Direction.NORTH),
            anchor(level, b9, Direction.DOWN, Direction.NORTH));
        Folds.setTension(f9, Fold.DAMPED);
        // 10. Un entonnoir qui se déverse dans un bloc cousu : les lingots arrivent dans un coffre à 1 500 blocs.
        BlockPos a10 = new BlockPos(sx + 110, Y, sz), b10 = new BlockPos(sx + 1610, Y, sz), chest = b10.east();
        set(level, a10.getX(), Y, sz, Blocks.STONE);
        set(level, b10.getX(), Y, sz, Blocks.STONE);
        set(level, chest.getX(), Y, sz, Blocks.CHEST);
        Fold f10 = Folds.create(TESTER, anchor(level, a10, Direction.UP, Direction.NORTH),
            anchor(level, b10, Direction.EAST, Direction.NORTH));
        set(level, a10.getX(), Y + 1, sz, Blocks.HOPPER);
        if (level.getBlockEntity(a10.above()) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity h) {
            h.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        }
        // 11. La teinture : le fil change de couleur, les coutures sont refaites.
        Folds.recolor(f2, Fold.DYE_BASE + net.minecraft.world.item.DyeColor.RED.getId());

        log(Folds.all().size() == 10 && f4 != null, "dix plis cousus, dont un vers le Nether et un 3×3");
        log(f7.size == 3 && Folds.find(level, a7.east().south(), Direction.UP) != null
            && Folds.find(level, a7.east(2), Direction.UP) == null, "la grande couture couvre 3×3 faces, pas plus");
        log(f7.mapCell(f7.a, a7.east().south()).equals(new BlockPos(b7.getX(), Y, sz + 1)), "chaque carré a son jumeau");
        log(Folds.forcedChunks() >= 8, "les tronçons des plis restent chargés (" + Folds.forcedChunks() + ")");
        log(java.nio.file.Files.exists(com.ourlet.OurletConfig.file()) && com.ourlet.OurletConfig.maxFolds == 12,
            "config/ourlet.properties écrit avec les valeurs par défaut");
        int advs = 0;
        for (String a : new String[]{"root", "sew", "pass", "fling", "far", "dimensions", "relay", "snip", "pocket",
            "patchwork", "haute_couture", "named", "spring", "delivery", "loop"}) {
            //#if mc26
            //$$ if (server.getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("ourlet", a)) != null) advs++;
            //#elif mc1201
            //$$ if (server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation("ourlet", a)) != null) advs++;
            //#else
            if (server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ourlet", a)) != null) advs++;
            //#endif
        }
        log(advs == 15, "les 15 progrès sont chargés (" + advs + ")");
        log(f4 != null && f4.crossDimension(), "le pli 4 relie deux dimensions");
        log(!f6.canEnter(f6.a) && f6.canEnter(f6.b), "le pli 6 est à sens unique");
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
            ItemEntity item4 = drop(level, a4), item5 = drop(level, a5), item6 = drop(level, a6);
            ItemEntity item7 = new ItemEntity(level, a7.getX() + 1.5, Y + 1.6, sz + 1.5, new ItemStack(Items.GOLD_INGOT));
            item7.setDeltaMovement(0, -0.25, 0);
            item7.setPickUpDelay(100);
            level.addFreshEntity(item7);
            ItemEntity item8 = drop(level, a8), item9 = drop(level, a9);
            CHECKS.add(new Check("la grande couture fait ressortir l'objet par le coin jumeau", t + 80, () -> {
                var ex = com.ourlet.fold.Transit.TEST_EXITS.get(item7.getId());
                Ourlet.LOGGER.info("[SELFTEST] sortie 3×3 : {}", ex);
                return ex != null && Math.abs(ex.pos().x - b7.getX() - 1.155) < 0.05 && Math.abs(ex.pos().z - (sz + 1.5)) < 0.3
                    && ex.pos().y >= Y - 0.01 && ex.pos().y < Y + 1;
            }));
            CHECKS.add(new Check("le fil élastique lance plus fort", t + 80, () -> {
                var ex = com.ourlet.fold.Transit.TEST_EXITS.get(item8.getId());
                Ourlet.LOGGER.info("[SELFTEST] sortie élastique : {}", ex);
                return ex != null && ex.velocity().x > 0.6;
            }));
            CHECKS.add(new Check("le fil ciré fait tomber sans fin, doucement", t + 80, () -> {
                int n = com.ourlet.fold.Transit.TEST_COUNTS.getOrDefault(item9.getId(), 0);
                Ourlet.LOGGER.info("[SELFTEST] chute sans fin : {} passages, y={}", n, item9.getY());
                return n >= 3 && item9.isAlive() && item9.getY() > Y && item9.getY() < Y + 5;
            }));
            CHECKS.add(new Check("l'entonnoir envoie ses lingots dans le coffre, de l'autre côté", t + 80, () -> {
                var c = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, chest);
                int iron = 0;
                if (c != null) for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.IRON_INGOT)) iron += c.getItem(i).getCount();
                Ourlet.LOGGER.info("[SELFTEST] lingots dans le coffre : {} (transportés : {})", iron, com.ourlet.fold.Hoppers.delivered());
                return iron == 3;
            }));
            CHECKS.add(new Check("le fil teint en rouge", t + 80, () ->
                f2.color() == Fold.DYE_BASE + net.minecraft.world.item.DyeColor.RED.getId() && Markers.count(f2) == 2));
            CHECKS.add(new Check("l'objet passe dans le Nether et ressort du mur, vers l'est", t + 80, () -> {
                var ex = com.ourlet.fold.Transit.TEST_EXITS.get(item4.getId());
                Ourlet.LOGGER.info("[SELFTEST] sortie dans le Nether : {}", ex);
                return ex != null && Math.abs(ex.pos().x - b4.getX() - 1.155) < 0.05 && ex.velocity().x > 0.2;
            }));
            CHECKS.add(new Check("le pli alimenté par la redstone est fermé", t + 80, () ->
                f5.powered() && !com.ourlet.fold.Transit.TEST_EXITS.containsKey(item5.getId()) && item5.isAlive()
                    && item5.getY() >= Y + 0.9 && item5.getY() < Y + 1.3));
            CHECKS.add(new Check("le pli à sens unique refuse l'entrée par A", t + 80, () ->
                !com.ourlet.fold.Transit.TEST_EXITS.containsKey(item6.getId()) && item6.isAlive() && item6.getY() < Y + 1.3));
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
            CHECKS.add(new Check("les coutures sont visibles (36 affichages)", t + 70, () -> {
                int n = 0;
                for (Fold f : new Fold[]{f1, f2, f3, f4f, f5, f6, f7, f8, f9, f10}) if (f != null) n += Markers.count(f);
                Ourlet.LOGGER.info("[SELFTEST] affichages : {}", n);
                return n == 36;
            }));
            CHECKS.add(new Check("les plis sont sauvegardés", t + 80, () -> savedLines(server) >= 10));
        });
    }

    private static ItemEntity drop(ServerLevel level, BlockPos onto) {
        ItemEntity item = new ItemEntity(level, onto.getX() + 0.5, onto.getY() + 1.6, onto.getZ() + 0.5, new ItemStack(Items.EMERALD));
        item.setDeltaMovement(0, -0.25, 0);
        item.setPickUpDelay(100);
        level.addFreshEntity(item);
        return item;
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
        if (pocketWatch && now > pocketDeadline) {
            pocketWatch = false;
            boolean gone = !Folds.all().contains(shortPocket);
            Ourlet.LOGGER.info("[SELFTEST] POCKET: {}", gone ? "OK" : "FAIL (le pli de poche ne s'est pas défait)");
        }
    }

    /** L'atelier du joueur de test : un mur de laine cousu à une mine lointaine, et un sol cousu à un plafond. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer sp)) return;
        ServerLevel level = Compat.level(sp);
        MinecraftServer server = level.getServer();
        Fold anyFold = Folds.all().isEmpty() ? null : Folds.all().get(0);
        boolean deniedBefore = anyFold != null && !Folds.mayEdit(sp, anyFold);
        //#if mc26
        //$$ server.getPlayerList().op(new net.minecraft.server.players.NameAndId(sp.getGameProfile()));
        //#else
        server.getPlayerList().op(sp.getGameProfile());
        //#endif
        sp.setGameMode(GameType.CREATIVE);
        log(deniedBefore && Folds.mayEdit(sp, anyFold), "un pli protégé : refusé à un autre joueur, permis à un opérateur");
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
        ItemStack thimble = new ItemStack(OurletItems.THIMBLE.get());
        sp.getInventory().setItem(3, thimble);
        sp.getInventory().setItem(4, new ItemStack(Items.SHEARS));
        //#if mc26
        //$$ sp.getInventory().setSelectedSlot(0);
        //#else
        sp.getInventory().selected = 0;
        //#endif
        sp.setShiftKeyDown(false);
        var r1 = NeedleItem.sew(sp, level, wallA, Direction.NORTH, needle, InteractionHand.MAIN_HAND);
        var r2 = NeedleItem.sew(sp, level, wallB, Direction.SOUTH, needle, InteractionHand.MAIN_HAND);
        Folds.End wallEnd = Folds.find(level, wallA, Direction.NORTH);
        log(wallEnd != null, "l'aiguille coud le mur de l'atelier à la mine (" + r1 + ", " + r2 + ")");
        if (wallEnd != null) {
            // les outils du fil : teinture, étiquette, slime (deux fois : retour au fil ordinaire)
            ItemStack dye = new ItemStack(Items.LIGHT_BLUE_DYE);
            com.ourlet.event.ServerEvents.handleTool(sp, dye, wallEnd);
            ItemStack tag = new ItemStack(Items.NAME_TAG);
            //#if mc1201
            //$$ tag.setHoverName(net.minecraft.network.chat.Component.literal("Mine"));
            //#else
            tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Mine"));
            //#endif
            com.ourlet.event.ServerEvents.handleTool(sp, tag, wallEnd);
            com.ourlet.event.ServerEvents.handleTool(sp, new ItemStack(Items.SLIME_BALL), wallEnd);
            boolean elastic = wallEnd.fold().tension() == Fold.ELASTIC;
            com.ourlet.event.ServerEvents.handleTool(sp, new ItemStack(Items.SLIME_BALL), wallEnd);
            log(wallEnd.fold().color() == Fold.DYE_BASE + net.minecraft.world.item.DyeColor.LIGHT_BLUE.getId()
                && "Mine".equals(wallEnd.fold().name()) && elastic && wallEnd.fold().tension() == Fold.NORMAL,
                "teinture, étiquette et slime sur une couture");
        }
        // un second pli, pour les captures : le sol de l'atelier vers le plafond de la mine
        NeedleItem.sew(sp, level, new BlockPos(sx + 2, Y - 1, z0 + 1), Direction.UP, needle, InteractionHand.MAIN_HAND);
        NeedleItem.sew(sp, level, new BlockPos(mx - 2, Y + 3, z0 + 8), Direction.DOWN, needle, InteractionHand.MAIN_HAND);

        // le dé à coudre : la maison est le sol de la mine, le pli de poche s'ouvre dans l'atelier
        sp.setShiftKeyDown(true);
        ThimbleItem.pocket(sp, level, new BlockPos(mx + 2, Y - 1, z0 + 9), Direction.UP, thimble, InteractionHand.MAIN_HAND);
        sp.setShiftKeyDown(false);
        ThimbleItem.pocket(sp, level, new BlockPos(sx - 3, Y - 1, z0 + 3), Direction.UP, thimble, InteractionHand.MAIN_HAND);
        long pockets = Folds.all().stream().filter(f -> f.temporary()).count();
        ThimbleItem.pocket(sp, level, new BlockPos(sx - 2, Y - 1, z0 + 1), Direction.UP, thimble, InteractionHand.MAIN_HAND);
        long pockets2 = Folds.all().stream().filter(f -> f.temporary()).count();
        log(Folds.home(sp.getUUID()) != null && pockets == 1 && pockets2 == 1, "le dé à coudre ouvre un seul pli de poche à la fois");
        // un pli de poche court, pour vérifier qu'il se défait seul
        shortPocket = Folds.create(TESTER, Folds.anchor(level, new BlockPos(sx + 3, Y - 1, z0 - 2), Direction.UP, Direction.NORTH),
            Folds.anchor(level, new BlockPos(mx + 3, Y - 1, z0 + 10), Direction.UP, Direction.NORTH), 40);
        pocketWatch = true;
        pocketDeadline = level.getGameTime() + 60;

        // la vitrine (pour l'animation de la description) : une grande couture cirée, du sol au plafond,
        // où un mouton, une poule et de la laine tombent sans fin
        for (int x = mx + 4; x <= mx + 7; x++) {
            for (int z = z0 + 3; z <= z0 + 10; z++) {
                set(level, x, Y - 1, z, Blocks.STONE);
                set(level, x, Y + 3, z, Blocks.DEEPSLATE);
                for (int y = Y; y <= Y + 2; y++) set(level, x, y, z, Blocks.AIR);
            }
        }
        BlockPos vf = new BlockPos(mx + 5, Y - 1, z0 + 8), vc = new BlockPos(mx + 5, Y + 3, z0 + 8);
        Fold vitrine = Folds.create(TESTER, Folds.anchor(level, vf, Direction.UP, Direction.NORTH),
            Folds.anchor(level, vc, Direction.DOWN, Direction.NORTH), -1, 3);
        Folds.setTension(vitrine, Fold.DAMPED);
        Folds.recolor(vitrine, Fold.DYE_BASE + net.minecraft.world.item.DyeColor.MAGENTA.getId());
        Compat.command(level, String.format(java.util.Locale.ROOT, "summon minecraft:sheep %d.5 %d %d.5 {Color:6b}", mx + 5, Y + 1, z0 + 8));
        Compat.command(level, String.format(java.util.Locale.ROOT, "summon minecraft:chicken %d.5 %d %d.5", mx + 4, Y + 2, z0 + 9));
        String[] wool = {"minecraft:pink_wool", "minecraft:light_blue_wool", "minecraft:yellow_wool", "minecraft:string"};
        for (int i = 0; i < wool.length; i++) {
            Compat.command(level, String.format(java.util.Locale.ROOT, "summon minecraft:item %.2f %.2f %.2f {Item:%s,PickupDelay:32767}",
                mx + 4.4 + (i % 2) * 1.9, Y + 0.6 + i * 0.6, z0 + 7.5 + (i / 2) * 1.8, Compat.itemNbt(wool[i])));
        }

        Compat.teleportPlayer(sp, level, sx + 0.5, Y, z0 + 1.5, 0f, 3f);
        var gaze = com.ourlet.fold.Gaze.describe(sp);
        Ourlet.LOGGER.info("[SELFTEST] regard : {}", gaze == null ? null : gaze.getString());
        log(gaze != null && gaze.getString().contains(String.valueOf(mx)), "viser la couture montre où elle mène");
        relayWatch = true;
        relayDeadline = level.getGameTime() + 20 * 120;
        Ourlet.LOGGER.info("[SELFTEST] atelier prêt pour {} (mur cousu {}, minerai {})", sp.getName().getString(),
            wallA.toShortString(), ore.toShortString());
    }
}
