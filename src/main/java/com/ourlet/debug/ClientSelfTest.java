package com.ourlet.debug;

import com.ourlet.Ourlet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Test automatique côté client (-Dourlet.clienttest=true), pour l'intégration continue :
 * le joueur regarde le mur cousu (capture), le casse à travers le pli (le mur doit rester),
 * puis marche dedans et doit se retrouver à 3 000 blocs (capture).
 * Inscrit sur le bus du jeu pendant FMLClientSetupEvent (voir Ourlet).
 */
public final class ClientSelfTest {

    private ClientSelfTest() {}

    private static final boolean ENABLED = Boolean.getBoolean("ourlet.clienttest");
    /** Captures en rafale pour l'animation de la description (seulement si -Dourlet.gif=true). */
    private static final boolean GIF = Boolean.getBoolean("ourlet.gif");
    private static String rec;
    private static int recN;

    private record Step(String label, BooleanSupplier ready, Runnable action, int minWait) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean built;
    private static int inGame, waited, failures;
    private static double startX;
    private static BlockPos wall;
    /** Où le joueur est ressorti, dans la mine. */
    private static Vec3 arrival;
    /** Un lent mouvement de caméra (pour l'animation). */
    private static float panYaw0, panYaw1, panPitch0, panPitch1;
    private static int panStart = -1, panTicks;

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void step(String label, int wait, Runnable action) {
        STEPS.add(new Step(label, () -> true, action, wait));
    }

    private static void stepWhen(String label, BooleanSupplier ready, int wait, Runnable action) {
        STEPS.add(new Step(label, ready, action, wait));
    }

    private static void shot(String name) {
        Minecraft mc = mc();
        //#if mc26
        //$$ mc.gui.toastManager().clear();
        //#else
        mc.getToasts().clear();
        //#endif
        //#if mc26
        //$$ Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
        //$$     c -> Ourlet.LOGGER.info("[CLIENTTEST] capture {}", name));
        //#else
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
            c -> Ourlet.LOGGER.info("[CLIENTTEST] capture {}", name));
        //#endif
    }

    /** Commence (ou arrête, avec null) une rafale de captures : une toutes les 2 ticks. */
    private static void record(String scene) {
        if (!GIF) return;
        rec = scene;
        recN = 0;
        if (scene != null) com.ourlet.client.ClientCompat.clearChat();
        Ourlet.LOGGER.info("[CLIENTTEST] rafale {}", scene == null ? "terminée" : scene);
    }

    private static void look(float yaw, float pitch) {
        var p = mc().player;
        if (p == null) return;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.yHeadRot = yaw;
        p.yHeadRotO = yaw;
    }

    /** Tourne la tête doucement vers (yaw, pitch) en {@code ticks} ticks. */
    private static void pan(float yaw, float pitch, int ticks) {
        var p = mc().player;
        panYaw0 = p.getYRot();
        panPitch0 = p.getXRot();
        panYaw1 = yaw;
        panPitch1 = pitch;
        panTicks = ticks;
        panStart = inGame;
    }

    /** Le centre de la vitrine (la grande couture cirée), à cinq blocs à l'est de la caméra. */
    private static BlockPos vitrine() {
        return BlockPos.containing(arrival.x + 5, arrival.y, Math.floor(arrival.z) + 3.5);
    }

    private static void check(boolean ok, String what) {
        if (ok) Ourlet.LOGGER.info("[CLIENTTEST] ok : {}", what);
        else {
            failures++;
            Ourlet.LOGGER.error("[CLIENTTEST] ÉCHEC : {}", what);
        }
    }

    private static void build() {
        built = true;
        step("regarder le mur cousu", 1, () -> {
            look(0f, 3f);
            if (GIF) com.ourlet.client.ClientCompat.hideChat(true);
            record("a");
        });
        step("capture de la couture", 30, () -> shot("01_seam_wall"));
        step("casser à travers le pli", 10, () -> {
            HitResult h = mc().hitResult;
            check(h instanceof BlockHitResult && h.getType() == HitResult.Type.BLOCK, "le joueur vise un bloc");
            if (h instanceof BlockHitResult bh) {
                wall = bh.getBlockPos();
                mc().gameMode.startDestroyBlock(wall, bh.getDirection());
            }
        });
        step("le mur cousu est intact", 30, () -> {
            check(wall != null && !mc().level.getBlockState(wall).isAir(), "le mur cousu n'a pas cassé");
            shot("02_after_relay");
            startX = mc().player.getX();
            look(0f, 3f);
            mc().options.keyUp.setDown(true);
        });
        stepWhen("traverser le pli", () -> Math.abs(mc().player.getX() - startX) > 1000, 1, () -> {
            mc().options.keyUp.setDown(false);
            record(null);
            arrival = mc().player.position();
            check(true, "le joueur a traversé le pli (" + (int) startX + " -> " + (int) mc().player.getX() + ")");
        });
        // à 3 000 blocs, le client attend ses tronçons : on ne filme qu'une fois la mine dessinée
        stepWhen("la mine est dessinée", () -> waited > 240
            || com.ourlet.client.ClientCompat.rendered(BlockPos.containing(arrival)) && com.ourlet.client.ClientCompat.rendered(vitrine()), 10, () -> {
            var p = mc().player;
            Ourlet.LOGGER.info("[CLIENTTEST] dans la mine : {} (arrivé en {})", p.position(), arrival);
            // trois pas devant le mur de la mine, face à la couture par laquelle on est arrivé
            p.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f 180 3",
                arrival.x, arrival.y, Math.floor(arrival.z) + 3.5));
        });
        step("face à la couture", 8, () -> {
            look(180f, 3f);
            record("b");
        });
        step("vers la vitrine", 24, () -> {
            // puis on tourne la tête vers la grande couture cirée où le mouton tombe sans fin
            record("c");
            pan(270f, 7f, 36);
        });
        step("fin de la vitrine", 36 + 64, () -> {
            record(null);
            shot("07_endless_fall");
        });
        step("de l'autre côté", 2, () -> look(180f, 3f));
        step("capture de l'autre côté", 20, () -> shot("03_other_side"));
        step("ouvrir le carnet", 5, () -> {
            check(com.ourlet.client.CarnetScreen.chapters().size() == 11, "le carnet a 11 chapitres");
            var sc = new com.ourlet.client.CarnetScreen(0, null);
            if (GIF) sc.play(1, 0);
            else sc.jump(1, 0, 34);
            com.ourlet.client.ClientCompat.open(sc);
            record("d");
        });
        step("feuilleter le carnet", GIF ? 80 : 1, () -> record(null));
        step("capture du carnet (passage)", 2, () -> {
            if (com.ourlet.client.ClientCompat.screen() instanceof com.ourlet.client.CarnetScreen sc) sc.jump(1, 0, 34);
        });
        step("capture du carnet (passage) 2", 20, () -> shot("04_carnet_pass"));
        step("carnet : à travers le pli", 2, () -> {
            if (com.ourlet.client.ClientCompat.screen() instanceof com.ourlet.client.CarnetScreen sc) sc.jump(3, 3, 40);
            check(com.ourlet.client.ClientCompat.screen() instanceof com.ourlet.client.CarnetScreen, "le carnet est ouvert");
        });
        step("capture du carnet (relais)", 20, () -> shot("05_carnet_relay"));
        step("carnet : ciseaux", 2, () -> {
            if (com.ourlet.client.ClientCompat.screen() instanceof com.ourlet.client.CarnetScreen sc) sc.jump(9, 3, 60);
        });
        step("capture du carnet (redstone)", 20, () -> {
            shot("06_carnet_control");
            if (com.ourlet.client.ClientCompat.screen() instanceof com.ourlet.client.CarnetScreen sc) sc.jump(4, 2, 50);
        });
        step("capture du carnet (3×3)", 20, () -> {
            shot("08_carnet_patchwork");
            if (com.ourlet.client.ClientCompat.screen() instanceof com.ourlet.client.CarnetScreen sc) sc.jump(7, 1, 55);
        });
        step("capture du carnet (entonnoirs)", 20, () -> shot("09_carnet_hoppers"));
        step("fermer le carnet", 5, () -> com.ourlet.client.ClientCompat.open(null));
        step("fin", 10, () -> {
            Ourlet.LOGGER.info("[CLIENTTEST] RESULT: {}", failures == 0 ? "OK" : "FAIL " + failures);
            mc().stop();
        });
    }

    //#if mc1201
    //$$ @SubscribeEvent
    //$$ public static void onTick(TickEvent.ClientTickEvent event) {
    //$$     if (event.phase == TickEvent.Phase.END) tick();
    //$$ }
    //#else
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent.Post event) {
        tick();
    }
    //#endif

    private static void tick() {
        if (!ENABLED) return;
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;
        inGame++;
        if (inGame < 160) return;
        if (!built) {
            build();
            Ourlet.LOGGER.info("[CLIENTTEST] début ({} étapes)", STEPS.size());
        }
        if (panStart >= 0) {
            float t = Math.min(1f, (inGame - panStart) / (float) panTicks);
            float e = t * t * (3 - 2 * t);
            look(panYaw0 + (panYaw1 - panYaw0) * e, panPitch0 + (panPitch1 - panPitch0) * e);
            if (t >= 1f) panStart = -1;
        }
        if (rec != null && inGame % 2 == 0) shot(String.format("gif_%s_%03d", rec, recN++));
        Step s = STEPS.peek();
        if (s == null) return;
        waited++;
        if (waited < s.minWait || !s.ready.getAsBoolean()) {
            if (waited > 1200) {
                check(false, "délai dépassé à l'étape « " + s.label + " »");
                mc.options.keyUp.setDown(false);
                STEPS.poll();
                waited = 0;
            }
            return;
        }
        STEPS.poll();
        waited = 0;
        try {
            s.action.run();
        } catch (Throwable t) {
            check(false, "exception à l'étape « " + s.label + " » : " + t);
            Ourlet.LOGGER.error("[CLIENTTEST]", t);
        }
    }
}
