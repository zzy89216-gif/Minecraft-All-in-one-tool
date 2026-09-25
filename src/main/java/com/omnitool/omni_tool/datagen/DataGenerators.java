package com.omnitool.omni_tool.datagen;

import com.omnitool.omni_tool.OmniToolMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;

/**
 * Central data generation entry point.
 *
 * <p>Everything that can be generated is generated, so the reference resources for the six vanilla
 * tiers are reproducible instead of hand written:
 * <ul>
 *   <li>{@code models/item/omni_tool_*.json} - item models (client)</li>
 *   <li>{@code lang/en_us.json} and {@code lang/zh_cn.json} - bilingual names (client)</li>
 *   <li>{@code recipes/omni_tool_*.json} plus their advancements (server)</li>
 *   <li>{@code tags/items/omni_tools.json} - the mod's own item tag (server)</li>
 * </ul>
 *
 * <p>Run with {@code ./gradlew runData}; the output lands in {@code src/generated/resources} which is
 * registered as a resource source set by {@code build.gradle}.
 */
@Mod.EventBusSubscriber(modid = OmniToolMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        OmniToolMod.LOGGER.info("[OmniTool] Gathering data (client={}, server={})",
                event.includeClient(), event.includeServer());

        if (event.includeClient()) {
            generator.addProvider(true, new ModItemModelProvider(packOutput, existingFileHelper));
            generator.addProvider(true, new ModLanguageProvider(packOutput, ModLanguageProvider.EN_US));
            generator.addProvider(true, new ModLanguageProvider(packOutput, ModLanguageProvider.ZH_CN));
        }
        if (event.includeServer()) {
            generator.addProvider(true, new ModRecipeProvider(packOutput));
            generator.addProvider(true, new ModItemTagsProvider(packOutput, lookupProvider,
                    existingFileHelper));
        }
    }
}
