package com.ourlet.carnet;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * La ligne « Maintiens [W] pour feuilleter le carnet » des infobulles, comme « Hold [W] to Ponder »
 * de Create. Classe commune (sans code client) : le client y écrit seulement la progression.
 */
public final class CarnetHint {

    private CarnetHint() {}

    /** 0 → 1 pendant que la touche est maintenue au-dessus d'un objet d'Ourlet (écrit par le client). */
    public static volatile float progress;

    public static void tooltip(Consumer<Component> out) {
        int filled = Math.round(progress * 12);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 12; i++) bar.append(i < filled ? '▮' : '▯');
        out.accept(Component.literal(""));
        out.accept(Component.translatable("ourlet.carnet.hold", Component.literal("W").withStyle(ChatFormatting.WHITE))
            .withStyle(ChatFormatting.DARK_GRAY));
        if (progress > 0) out.accept(Component.literal(bar.toString()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
