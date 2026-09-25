package com.omnitool.omni_tool;

import com.mojang.logging.LogUtils;
import com.omnitool.omni_tool.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Omni Tool - an all-in-one tool for Minecraft 1.20.1 (Forge 47.x).
 *
 * <p>One item that mines like a pickaxe, an axe and a shovel, and hits like a slightly slower sword.
 * The six vanilla tiers are registered statically and their resources are generated; any material
 * that can craft a pickaxe - including materials added by other mods - gets a matching Omni Tool
 * through the runtime discovery layer.
 *
 * <h2>Layers</h2>
 * <ul>
 *   <li>{@code registry} - material description, static registration, runtime discovery of modded
 *       tiers</li>
 *   <li>{@code item} - {@link com.omnitool.omni_tool.item.OmniToolItem}, the tool behaviour itself</li>
 *   <li>{@code recipe} - {@link com.omnitool.omni_tool.recipe.OmniToolRecipeCloner}, dynamic recipe
 *       cloning</li>
 *   <li>{@code event} - event wiring (recipe injection, creative tab)</li>
 *   <li>{@code client} - client-only fallback models for dynamically registered tools</li>
 *   <li>{@code datagen} - data generation for the vanilla tiers</li>
 * </ul>
 *
 * <p>See {@code HANDOFF.md} for the full architecture diagram and the known limitations.
 */
@Mod(OmniToolMod.MOD_ID)
public final class OmniToolMod {

    /** Mod id; also the namespace of every resource this mod creates. */
    public static final String MOD_ID = "omni_tool";

    /** Shared logger, also used by the data generators. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public OmniToolMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Static layer: the six vanilla tiers.
        ModItems.ITEMS.register(modBus);

        modBus.addListener(this::commonSetup);

        LOGGER.info("[OmniTool] Constructed (mod id '{}')", MOD_ID);
    }

    /**
     * Runs after every registry has been filled, which means the dynamic layer has already created
     * its tools. The tier lookup is built here so recipe cloning can resolve materials later on.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModItems.init();
            LOGGER.info("[OmniTool] Common setup complete: {} static + {} dynamic Omni Tool(s)",
                    ModItems.staticTools().size(), ModItems.dynamicTools().size());
        });
    }
}
