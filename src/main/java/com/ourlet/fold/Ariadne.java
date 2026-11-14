package com.ourlet.fold;

import com.ourlet.platform.Compat;
import com.ourlet.registry.OurletItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Le fil d'Ariane : avec le dé à coudre en main, une ligne de points blancs (visible de toi seul)
 * montre la direction de la maison ; avec l'aiguille et un premier point en attente, elle montre
 * ce premier point. Seulement dans la même dimension.
 */
public final class Ariadne {

    private Ariadne() {}

    private static boolean holds(ServerPlayer p, net.minecraft.world.item.Item item) {
        return p.getItemInHand(InteractionHand.MAIN_HAND).getItem() == item
            || p.getItemInHand(InteractionHand.OFF_HAND).getItem() == item;
    }

    static void tick(ServerLevel level) {
        String dim = Compat.dim(level);
        for (ServerPlayer p : level.players()) {
            if (holds(p, OurletItems.THIMBLE.get())) {
                Anchor home = Folds.home(p.getUUID());
                if (home != null && home.dim().equals(dim)) Effects.ariadne(p, home.center(), Effects.rgb(Fold.BASTING));
            } else if (holds(p, OurletItems.NEEDLE.get())) {
                Folds.Pending pending = Folds.pending(p.getUUID());
                if (pending != null && pending.anchor().dim().equals(dim)) Effects.ariadne(p, pending.anchor().center(), 0xEDE3CF);
            }
        }
    }
}
