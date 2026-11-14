package com.ourlet.registry;

import com.ourlet.Ourlet;
import com.ourlet.item.NeedleItem;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/** Objets du mod : l'Aiguille Spatiale, le Dé à coudre, la Bobine, et les fils (uniquement pour dessiner les coutures). */
public final class OurletItems {

    private OurletItems() {}

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Ourlet.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Ourlet.MOD_ID);

    public static final Supplier<Item> NEEDLE = item("spatial_needle",
        p -> new NeedleItem(p.durability(NeedleItem.DURABILITY).rarity(Rarity.UNCOMMON)));

    public static final Supplier<Item> THIMBLE = item("thimble",
        p -> new com.ourlet.item.ThimbleItem(p.durability(com.ourlet.item.ThimbleItem.DURABILITY).rarity(Rarity.UNCOMMON)));

    /** La bobine : tenue dans l'autre main, l'aiguille fait une grande couture 3×3. */
    public static final Supplier<Item> SPOOL = item("spool", p -> new Item(p.stacksTo(16)));

    /**
     * Couleurs de fil : les deux bouts d'un même pli portent la même couleur. Les quatre premières
     * sont choisies au hasard à la couture, « basting » est le fil de bâti des plis de poche,
     * les seize suivantes viennent des teintures (même ordre que les teintures vanilla).
     */
    public static final String[] THREADS = {"rose", "indigo", "saffron", "sage", "basting",
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    @SuppressWarnings("unchecked")
    public static final Supplier<Item>[] THREAD_ITEMS = new Supplier[THREADS.length];

    static {
        for (int i = 0; i < THREADS.length; i++) {
            THREAD_ITEMS[i] = item("seam_" + THREADS[i], p -> new Item(p.stacksTo(1)));
        }
    }

    /** L'onglet « Ourlet » du mode créatif, avec l'aiguille pour icône. */
    @SuppressWarnings("unchecked")
    public static final Supplier<net.minecraft.world.item.CreativeModeTab> TAB = TABS.register("ourlet", () ->
        net.minecraft.world.item.CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.ourlet"))
            .icon(() -> new ItemStack(NEEDLE.get()))
            .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .displayItems((params, out) -> {
                out.accept(NEEDLE.get());
                out.accept(THIMBLE.get());
                out.accept(SPOOL.get());
            })
            .build());

    private static Supplier<Item> item(String name, java.util.function.Function<Item.Properties, Item> factory) {
        //#if mc26
        //$$ return ITEMS.register(name, id -> factory.apply(new Item.Properties()
        //$$     .setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id))));
        //#else
        return ITEMS.register(name, () -> factory.apply(new Item.Properties()));
        //#endif
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(NEEDLE.get()));
            event.accept(new ItemStack(THIMBLE.get()));
            event.accept(new ItemStack(SPOOL.get()));
        }
    }

    /** Identifiant du fil de couleur {@code color}, pour la commande /summon. */
    public static String threadId(int color) {
        return Ourlet.MOD_ID + ":seam_" + THREADS[Math.floorMod(color, THREADS.length)];
    }
}
