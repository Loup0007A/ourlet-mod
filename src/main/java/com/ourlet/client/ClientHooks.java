package com.ourlet.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ourlet.carnet.CarnetHint;
import com.ourlet.registry.OurletItems;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * « Maintiens [W] pour feuilleter le carnet » : au-dessus d'un objet d'Ourlet dans un inventaire,
 * garder W enfoncé remplit la jauge de l'infobulle puis ouvre le Carnet de couture (comme Ponder).
 * Inscrit sur le bus du jeu pendant FMLClientSetupEvent (voir Ourlet).
 */
public final class ClientHooks {

    private ClientHooks() {}

    private static final int HOLD_TICKS = 14;
    private static int held;

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
        String chapter = null;
        if (ClientCompat.screen() instanceof AbstractContainerScreen<?> s) {
            ItemStack stack = ClientCompat.hovered(s);
            if (!stack.isEmpty()) {
                if (stack.getItem() == OurletItems.NEEDLE.get()) chapter = "sew";
                else if (stack.getItem() == OurletItems.THIMBLE.get()) chapter = "thimble";
            }
        }
        if (chapter != null && ClientCompat.keyDown(InputConstants.KEY_W)) {
            held++;
            if (held >= HOLD_TICKS) {
                held = 0;
                CarnetHint.progress = 0;
                CarnetScreen.open(chapter, ClientCompat.screen());
                return;
            }
        } else {
            held = Math.max(0, held - 2);
        }
        CarnetHint.progress = held / (float) HOLD_TICKS;
    }
}
