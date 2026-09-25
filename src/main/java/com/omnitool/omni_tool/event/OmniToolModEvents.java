package com.omnitool.omni_tool.event;

import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.registry.ModItems;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mod-bus wiring that is not related to data generation.
 *
 * <p>Every Omni Tool - the six vanilla tiers and everything the dynamic layer discovered - is
 * appended to the vanilla "Tools &amp; Utilities" tab so the items are reachable in creative mode
 * without a custom tab.
 */
@Mod.EventBusSubscriber(modid = OmniToolMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OmniToolModEvents {

    private OmniToolModEvents() {
    }

    @SubscribeEvent
    public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            return;
        }
        for (Item item : ModItems.allTools()) {
            event.accept(item, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
