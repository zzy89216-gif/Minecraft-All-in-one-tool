package com.omnitool.omni_tool.client;

import com.mojang.logging.LogUtils;
import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.item.OmniToolItem;
import com.omnitool.omni_tool.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * Client-only setup.
 *
 * <p>Data generation can only create resources for items that exist while {@code runData} runs -
 * that is, the six vanilla tiers. Items discovered from <i>other mods</i> at runtime have no model
 * file, which would render them as the purple/black "missing model" cube.
 *
 * <p>To avoid that, every dynamic tool is mapped to the model of the vanilla Omni Tool that matches
 * its harvest level (wood &lt;= 0, stone 1, iron 2, diamond 3, netherite 4). The tool keeps its own
 * name, stats and recipes; only the icon is borrowed. A resource pack can override this at any time
 * by shipping a model at {@code assets/omni_tool/models/item/<id>.json}.
 *
 * <p>The whole block is defensive: an unexpected mapping failure only costs the fallback icon.
 */
@Mod.EventBusSubscriber(modid = OmniToolMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OmniToolClientSetup {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String INVENTORY_VARIANT = "inventory";

    private OmniToolClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // ItemModelShaper must be touched on the client thread.
        event.enqueueWork(OmniToolClientSetup::registerFallbackModels);
    }

    private static void registerFallbackModels() {
        List<Item> dynamicTools = ModItems.dynamicTools();
        if (dynamicTools.isEmpty()) {
            return;
        }
        int registered = 0;
        try {
            ItemModelShaper shaper = Minecraft.getInstance().getItemRenderer().getItemModelShaper();
            for (Item item : dynamicTools) {
                if (item instanceof OmniToolItem omni) {
                    ModelResourceLocation model = new ModelResourceLocation(
                            new ResourceLocation(OmniToolMod.MOD_ID, fallbackModelName(omni)), INVENTORY_VARIANT);
                    shaper.register(item, model);
                    registered++;
                }
            }
        } catch (Throwable throwable) {
            LOGGER.warn("[OmniTool] Could not register fallback item models for dynamically "
                    + "registered Omni Tools; they may render as a missing model.", throwable);
        }
        if (registered > 0) {
            LOGGER.info("[OmniTool] Registered fallback models for {} dynamically registered Omni Tool(s)",
                    registered);
        }
    }

    /** Maps a harvest level onto the statically generated model that looks closest. */
    private static String fallbackModelName(OmniToolItem item) {
        return switch (item.getOmniMaterial().harvestLevel()) {
            case 4 -> "omni_tool_netherite";
            case 3 -> "omni_tool_diamond";
            case 2 -> "omni_tool_iron";
            case 1 -> "omni_tool_stone";
            default -> "omni_tool_wood";
        };
    }
}
