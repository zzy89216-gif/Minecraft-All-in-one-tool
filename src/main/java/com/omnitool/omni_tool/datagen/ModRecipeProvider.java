package com.omnitool.omni_tool.datagen;

import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.registry.ModItems;
import com.omnitool.omni_tool.registry.OmniToolMaterial;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Consumer;

/**
 * Recipes for the six statically registered Omni Tools.
 *
 * <h2>The fusion pattern</h2>
 * <pre>
 *      M M M        row 1: three material - the pickaxe head. It is the widest element and,
 *                   read the other way round, the cutting edge of the shovel's scoop.
 *      M S M        row 2: material on both sides - the axe's twin blades; the stick in the
 *                   middle starts the sword's central axis, which continues into row 3.
 *      . S .        row 3: a single stick - the grip that all four heads are welded onto.
 *
 *      M = the material of the tier, S = stick
 * </pre>
 * Why this pattern and not the pickaxe's "品" shape? The pickaxe leaves the centre of the top area
 * and the whole middle column free; here the middle column is <b>filled</b> (rows 1-3 read as one
 * continuous line: three heads above, one shaft through the middle, one grip below). That filled
 * cross is the visual signature of "four tools merged into one": a horizontal head (pickaxe/shovel),
 * two side blades (axe) and a vertical axis (sword) sharing a single handle.
 * It is also symmetric, which makes it easy to remember and impossible to confuse with any vanilla
 * tool pattern.
 *
 * <h2>Cost</h2>
 * 5 material + 2 sticks. That is deliberately more than the best single tool (pickaxe: 3 + 2) and
 * far less than the four tools it replaces (3 + 3 + 1 + 2 = 9 material, 8 sticks) - the price of
 * convenience, on top of the speed penalty described in
 * {@link OmniToolMaterial} ("multi functional, not specialised").
 *
 * <h2>Deviation from vanilla for netherite</h2>
 * Vanilla upgrades netherite gear in a smithing table. For consistency with the other five tiers -
 * and so that "a material that can craft a pickaxe can craft an Omni Tool" holds for every tier -
 * the netherite Omni Tool uses the same crafting-grid pattern with netherite ingots.
 *
 * <h2>Relation to the dynamic layer</h2>
 * This provider only covers the six vanilla tiers; these recipes are authoritative for them. The
 * runtime cloner skips any Omni Tool that is already craftable, so a modded pickaxe that reuses a
 * vanilla tier can never produce a second, competing recipe.
 */
public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
        List<RegistryObject<Item>> tools = ModItems.staticTools();
        List<OmniToolMaterial> materials = ModItems.staticMaterials();
        for (int index = 0; index < tools.size() && index < materials.size(); index++) {
            if (tools.get(index).isPresent()) {
                buildOmniToolRecipe(consumer, tools.get(index).get(), materials.get(index));
            }
        }
    }

    private void buildOmniToolRecipe(Consumer<FinishedRecipe> consumer, Item result,
                                     OmniToolMaterial material) {
        String materialKey = material.materialKey();
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, result)
                .define('M', materialIngredient(materialKey))
                .define('S', Items.STICK)
                .pattern("MMM")
                .pattern("MSM")
                .pattern(" S ")
                .group("omni_tool")
                .unlockedBy("has_material", materialCriterion(materialKey))
                .save(consumer, new ResourceLocation(OmniToolMod.MOD_ID, material.id()));
    }

    /** The material a tier is built from. */
    static Ingredient materialIngredient(String materialKey) {
        return switch (materialKey) {
            case "wood" -> Ingredient.of(ItemTags.PLANKS);
            case "stone" -> Ingredient.of(Items.COBBLESTONE);
            case "iron" -> Ingredient.of(Items.IRON_INGOT);
            case "gold" -> Ingredient.of(Items.GOLD_INGOT);
            case "diamond" -> Ingredient.of(Items.DIAMOND);
            case "netherite" -> Ingredient.of(Items.NETHERITE_INGOT);
            default -> Ingredient.of(Items.STICK);
        };
    }

    /**
     * Advancement trigger that unlocks the recipe once the player owns the material.
     *
     * <p>The dynamic layer derives a material key from a pickaxe id by stripping the vanilla
     * {@code _pickaxe} suffix ({@code tin_pickaxe} -> {@code tin}); this method mirrors that idea on
     * the explicit side by mapping the same keys onto concrete vanilla items.
     */
    private CriterionTriggerInstance materialCriterion(String materialKey) {
        return switch (materialKey) {
            case "wood" -> has(ItemTags.PLANKS);
            case "stone" -> has(Items.COBBLESTONE);
            case "iron" -> has(Items.IRON_INGOT);
            case "gold" -> has(Items.GOLD_INGOT);
            case "diamond" -> has(Items.DIAMOND);
            case "netherite" -> has(Items.NETHERITE_INGOT);
            default -> has(Items.STICK);
        };
    }
}
