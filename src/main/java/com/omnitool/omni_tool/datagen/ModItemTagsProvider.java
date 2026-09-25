package com.omnitool.omni_tool.datagen;

import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.concurrent.CompletableFuture;

/**
 * Emits the mod's own item tag, {@code omni_tool:omni_tools}.
 *
 * <h2>Why a tag?</h2>
 * Recipes and other mods should not have to enumerate six plus N item ids to talk about "any Omni
 * Tool". The tag is the stable, data driven handle for that, and it is generated here instead of
 * being hand written (requirement: tags come from data generation).
 *
 * <p>Block tags are not generated because this mod adds no blocks; the block tag lookup therefore
 * receives an empty future instead of a real provider.
 */
public class ModItemTagsProvider extends ItemTagsProvider {

    /** Tag containing every statically registered Omni Tool. */
    public static final TagKey<Item> OMNI_TOOLS = TagKey.create(Registries.ITEM,
            new ResourceLocation(OmniToolMod.MOD_ID, "omni_tools"));

    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                               ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, emptyBlockTags(), OmniToolMod.MOD_ID, existingFileHelper);
    }

    private static CompletableFuture<TagsProvider.TagLookup<Block>> emptyBlockTags() {
        return CompletableFuture.completedFuture(TagsProvider.TagLookup.empty());
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        TagsProvider.TagAppender<Item> appender = tag(OMNI_TOOLS);
        // Only the statically registered tools can be tagged here: dynamic tools are discovered at
        // runtime in a modded environment. They are added to the same tag through the tag file
        // convention of the source mod, or by a resource pack / datapack.
        for (Item item : ModItems.allTools()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null) {
                appender.add(ResourceKey.create(Registries.ITEM, id));
            }
        }
    }
}
