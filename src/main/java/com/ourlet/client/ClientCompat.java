package com.ourlet.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Le côté client de Compat : écran, touches, dessin dans l'interface. */
public final class ClientCompat {

    private ClientCompat() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static Screen screen() {
        //#if mc26
        //$$ return mc().gui.screen();
        //#else
        return mc().screen;
        //#endif
    }

    public static void open(Screen s) {
        //#if mc26
        //$$ mc().gui.setScreen(s);
        //#else
        mc().setScreen(s);
        //#endif
    }

    public static boolean keyDown(int key) {
        //#if mc26
        //$$ return InputConstants.isKeyDown(key);
        //#else
        return InputConstants.isKeyDown(mc().getWindow().getWindow(), key);
        //#endif
    }

    public static ItemStack hovered(AbstractContainerScreen<?> s) {
        //#if mc26
        //$$ var slot = s.getHoveredSlot();
        //#else
        var slot = s.getSlotUnderMouse();
        //#endif
        return slot == null ? ItemStack.EMPTY : slot.getItem();
    }

    public static ItemStack item(String id) {
        try {
            //#if mc26
            //$$ Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(id));
            //#else
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            //#endif
            return new ItemStack(item == null ? Items.BARRIER : item);
        } catch (RuntimeException e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    // ───────────────────────── dessin ─────────────────────────

    /** Une texture entière, étirée sur (x, y, w, h). */
    public static void image(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        //#if mc26
        //$$ g.blit(tex, x, y, x + w, y + h, 0f, 1f, 0f, 1f);
        //#else
        g.blit(tex, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        //#endif
    }

    public static void text(GuiGraphics g, Font font, FormattedCharSequence s, int x, int y, int argb, boolean shadow) {
        //#if mc26
        //$$ g.text(font, s, x, y, argb, shadow);
        //#else
        g.drawString(font, s, x, y, argb, shadow);
        //#endif
    }

    public static void text(GuiGraphics g, Font font, String s, int x, int y, int argb, boolean shadow) {
        //#if mc26
        //$$ g.text(font, s, x, y, argb, shadow);
        //#else
        g.drawString(font, s, x, y, argb, shadow);
        //#endif
    }

    public static void item(GuiGraphics g, ItemStack stack, int x, int y) {
        //#if mc26
        //$$ g.item(stack, x, y);
        //#else
        g.renderItem(stack, x, y);
        //#endif
    }

    /** Cache ou montre l'interface (F1), pour des captures propres. */
    public static void hideGui(boolean hide) {
        //#if !mc26
        mc().options.hideGui = hide;
        //#endif
    }

    /** Vide la discussion à l'écran. */
    public static void clearChat() {
        //#if !mc26
        mc().gui.getChat().clearMessages(false);
        //#endif
    }

    /** Cache la discussion (la barre d'action et les titres restent). */
    public static void hideChat(boolean hide) {
        mc().options.chatVisibility().set(hide ? net.minecraft.world.entity.player.ChatVisiblity.HIDDEN
            : net.minecraft.world.entity.player.ChatVisiblity.FULL);
    }

    /** Vrai quand le tronçon de {@code pos} est arrivé du serveur et qu'il a été dessiné. */
    public static boolean rendered(net.minecraft.core.BlockPos pos) {
        Minecraft mc = mc();
        if (mc.level == null || !mc.level.hasChunkAt(pos)) return false;
        //#if mc26
        //$$ return mc.levelRenderer.hasRenderedAllSections();
        //#elif mc1201
        //$$ return mc.levelRenderer.isChunkCompiled(pos);
        //#else
        return mc.levelRenderer.isSectionCompiled(pos);
        //#endif
    }
}
