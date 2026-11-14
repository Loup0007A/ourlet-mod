package com.ourlet;

import com.ourlet.event.ServerEvents;
import com.ourlet.registry.OurletItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
//#if forge
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//#endif
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ourlet — l'Aiguille Spatiale coud l'espace entre deux faces de blocs.
 *
 * Tout ce qui touche la face A ressort par la face B (et inversement), avec sa vitesse ;
 * on peut aussi casser, poser et actionner « à travers » le pli. Aucune nouvelle structure :
 * le pli n'existe que comme une couture sur deux blocs existants.
 *
 * La logique est côté serveur ; le client n'a besoin que des objets, textures et traductions.
 */
@Mod(Ourlet.MOD_ID)
public class Ourlet {

    public static final String MOD_ID = "ourlet";
    public static final Logger LOGGER = LoggerFactory.getLogger("Ourlet");

    //#if neoforge
    //$$ public Ourlet(net.neoforged.bus.api.IEventBus modBus, net.neoforged.fml.ModContainer container) {
    //$$     OurletItems.register(modBus);
    //$$     modBus.addListener(OurletItems::onCreativeTab);
    //$$     net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ServerEvents.class);
    //$$     net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.ourlet.debug.ServerSelfTest.class);
    //$$     if (FMLEnvironment.dist == Dist.CLIENT) {
    //$$         modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) ->
    //$$             net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.ourlet.debug.ClientSelfTest.class));
    //$$     }
    //$$ }
    //#else
    public Ourlet() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        OurletItems.register(modBus);
        modBus.addListener(OurletItems::onCreativeTab);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        MinecraftForge.EVENT_BUS.register(com.ourlet.debug.ServerSelfTest.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) ->
                MinecraftForge.EVENT_BUS.register(com.ourlet.debug.ClientSelfTest.class));
        }
    }
    //#endif

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
