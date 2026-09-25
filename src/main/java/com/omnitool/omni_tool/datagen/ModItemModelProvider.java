package com.omnitool.omni_tool.datagen;

import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.registry.ModItems;
import com.omnitool.omni_tool.registry.OmniToolMaterial;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * Item models for the statically registered Omni Tools.
 *
 * <h2>Texture strategy</h2>
 * The mod intentionally ships <b>no</b> texture files. Each model uses the vanilla
 * {@code item/handheld} parent and points its single layer at the corresponding vanilla pickaxe
 * texture ({@code minecraft:item/diamond_pickaxe} and friends), which:
 * <ul>
 *   <li>keeps the repository free of borrowed art assets (only a JSON reference, no copied PNG);</li>
 *   <li>makes the item instantly recognisable in the inventory and in the hand;</li>
 *   <li>is fully replaceable by a resource pack that ships
 *       {@code assets/omni_tool/models/item/omni_tool_<material>.json} plus a custom texture.</li>
 * </ul>
 * Tools discovered from other mods at runtime cannot be covered here (they do not exist during
 * {@code runData}); the client side maps them onto the closest generated model instead, see
 * {@link com.omnitool.omni_tool.client.OmniToolClientSetup}.
 */
public class ModItemModelProvider extends ItemModelProvider {

    /** Vanilla texture names do not always match our material keys (gold -> golden). */
    private static final String VANILLA_PICKAXE_SUFFIX = "pickaxe";

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, OmniToolMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        ResourceLocation handheld = new ResourceLocation("minecraft", "item/handheld");
        for (OmniToolMaterial material : ModItems.staticMaterials()) {
            ResourceLocation texture = new ResourceLocation("minecraft",
                    "item/" + vanillaTextureName(material.materialKey()));
            withExistingParent(material.id(), handheld).texture("layer0", texture);
        }
    }

    /**
     * Maps an Omni Tool material key onto the matching vanilla texture name.
     *
     * @param materialKey one of {@code wood, stone, iron, gold, diamond, netherite}
     * @return the vanilla item texture name, e.g. {@code golden_pickaxe}
     */
    static String vanillaTextureName(String materialKey) {
        String prefix = switch (materialKey) {
            case "wood" -> "wooden";
            case "gold" -> "golden";
            default -> materialKey;
        };
        return prefix + "_" + VANILLA_PICKAXE_SUFFIX;
    }
}
