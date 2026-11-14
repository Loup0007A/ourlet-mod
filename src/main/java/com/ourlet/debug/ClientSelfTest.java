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

    private record Step(String label, BooleanSupplier ready, Runnable action, int minWait) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean built;
    private static int inGame, waited, failures;
    private static double startX;
    private static BlockPos wall;

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
        //$$ Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
        //$$     c -> Ourlet.LOGGER.info("[CLIENTTEST] capture {}", name));
        //#else
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
            c -> Ourlet.LOGGER.info("[CLIENTTEST] capture {}", name));
        //#endif
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

    private static void check(boolean ok, String what) {
        if (ok) Ourlet.LOGGER.info("[CLIENTTEST] ok : {}", what);
        else {
            failures++;
            Ourlet.LOGGER.error("[CLIENTTEST] ÉCHEC : {}", what);
        }
    }

    private static void build() {
        built = true;
        step("regarder le mur cousu", 1, () -> look(0f, 3f));
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
        stepWhen("traverser le pli", () -> Math.abs(mc().player.getX() - startX) > 1000, 2, () -> {
            mc().options.keyUp.setDown(false);
            check(true, "le joueur a traversé le pli (" + (int) startX + " -> " + (int) mc().player.getX() + ")");
        });
        step("de l'autre côté", 60, () -> {
            look(180f, 10f);
        });
        step("capture de l'autre côté", 20, () -> shot("03_other_side"));
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
