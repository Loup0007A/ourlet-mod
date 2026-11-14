package com.ourlet.fold;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Les progrès d'Ourlet (onglet « Ourlet » des progrès). Ils sont attribués par la commande vanilla
 * /advancement, identique dans toutes les versions ; on retient ce qui a déjà été donné pendant
 * la session pour ne pas relancer la commande à chaque passage.
 */
public final class Progress {

    private Progress() {}

    private static final Map<UUID, Set<String>> GIVEN = new HashMap<>();

    public static void grant(ServerPlayer player, String id) {
        if (player == null) return;
        if (!GIVEN.computeIfAbsent(player.getUUID(), u -> new HashSet<>()).add(id)) return;
        var level = com.ourlet.platform.Compat.level(player);
        com.ourlet.platform.Compat.command(level, "advancement grant " + player.getName().getString() + " only ourlet:" + id);
    }

    public static void reset() {
        GIVEN.clear();
    }
}
