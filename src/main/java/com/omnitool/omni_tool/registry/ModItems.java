package com.omnitool.omni_tool.registry;

import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.item.OmniToolItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Item registration for the Omni Tool family.
 *
 * <h2>Two layers</h2>
 * <ol>
 *   <li><b>Static layer</b> - the six vanilla tiers (wood/stone/iron/gold/diamond/netherite) are
 *       registered eagerly here with a {@link DeferredRegister}. Their recipes, models, names and
 *       tags are produced by data generation, so the vanilla part is 100% deterministic
 *       (the "fallback" the requirements ask for).</li>
 *   <li><b>Dynamic layer</b> - {@link DynamicOmniToolRegistrar} scans the item registry at the end
 *       of the registration phase for <i>any</i> {@code PickaxeItem} whose {@link Tier} is not
 *       covered yet and registers a matching Omni Tool for it. That is what makes "any material
 *       that can craft a pickaxe can craft an Omni Tool" work for modded materials, without a
 *       hand written list.</li>
 * </ol>
 *
 * <p>Both layers are addressed through the same {@link Tier} -> {@link Item} lookup, so the recipe
 * cloner does not care where a tool came from.
 */
public final class ModItems {

    /** Registry names use this prefix for every Omni Tool (requirement 1). */
    public static final String ID_PREFIX = "omni_tool_";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OmniToolMod.MOD_ID);

    /** Statically registered tools, in creative-menu order. */
    private static final List<RegistryObject<Item>> STATIC_TOOLS = new ArrayList<>();

    /** Material descriptions of the statically registered tools, in registration order. */
    private static final List<OmniToolMaterial> STATIC_MATERIALS = new ArrayList<>();

    /** Vanilla tier -> registry object, used to seed the runtime lookup. */
    private static final Map<Tier, RegistryObject<Item>> STATIC_BY_TIER = new IdentityHashMap<>();

    /** Tools registered at runtime by the dynamic layer. */
    private static final List<Item> DYNAMIC_TOOLS = new ArrayList<>();

    /** Resolved tier -> item cache (populated lazily, see {@link #omniToolForTier}). */
    private static final Map<Tier, Item> TIER_LOOKUP = new IdentityHashMap<>();

    // ---------------------------------------------------------------------------------------------
    // Static (vanilla) registrations
    // ---------------------------------------------------------------------------------------------

    public static final RegistryObject<Item> OMNI_TOOL_WOOD =
            register("omni_tool_wood", Tiers.WOOD, "wood", false);
    public static final RegistryObject<Item> OMNI_TOOL_STONE =
            register("omni_tool_stone", Tiers.STONE, "stone", false);
    public static final RegistryObject<Item> OMNI_TOOL_IRON =
            register("omni_tool_iron", Tiers.IRON, "iron", false);
    public static final RegistryObject<Item> OMNI_TOOL_GOLD =
            register("omni_tool_gold", Tiers.GOLD, "gold", false);
    public static final RegistryObject<Item> OMNI_TOOL_DIAMOND =
            register("omni_tool_diamond", Tiers.DIAMOND, "diamond", false);
    public static final RegistryObject<Item> OMNI_TOOL_NETHERITE =
            register("omni_tool_netherite", Tiers.NETHERITE, "netherite", true);

    private ModItems() {
    }

    private static RegistryObject<Item> register(String id, Tier tier, String materialKey,
                                                 boolean fireResistant) {
        OmniToolMaterial material = OmniToolMaterial.ofVanilla(id, tier, materialKey);
        RegistryObject<Item> registered = ITEMS.register(id, () -> {
            Item.Properties properties = new Item.Properties();
            if (fireResistant) {
                properties = properties.fireResistant();
            }
            return new OmniToolItem(material, properties);
        });
        STATIC_TOOLS.add(registered);
        STATIC_MATERIALS.add(material);
        STATIC_BY_TIER.put(tier, registered);
        return registered;
    }

    // ---------------------------------------------------------------------------------------------
    // Lookups
    // ---------------------------------------------------------------------------------------------

    /**
     * Called once during common setup, after both registration layers have run.
     * The lookup is intentionally rebuilt from scratch so it also contains dynamic tools.
     */
    public static void init() {
        TIER_LOOKUP.clear();
        for (Map.Entry<Tier, RegistryObject<Item>> entry : STATIC_BY_TIER.entrySet()) {
            TIER_LOOKUP.put(entry.getKey(), entry.getValue().get());
        }
        for (Item item : DYNAMIC_TOOLS) {
            if (item instanceof OmniToolItem omni) {
                TIER_LOOKUP.put(omni.getOmniMaterial().tier(), item);
            }
        }
    }

    /**
     * Finds the Omni Tool belonging to a tier, if any.
     *
     * <p>The cache is filled by {@link #init()} and by the dynamic registrar; a cache miss triggers
     * one defensive scan of the item registry so a tool can never be "lost" because of event
     * ordering. A miss after that scan is a legitimate "no Omni Tool for this material" and the
     * caller must skip gracefully (never crash).
     */
    public static Optional<Item> omniToolForTier(Tier tier) {
        Item cached = TIER_LOOKUP.get(tier);
        if (cached != null) {
            return Optional.of(cached);
        }
        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof OmniToolItem omni && omni.getOmniMaterial().tier() == tier) {
                TIER_LOOKUP.put(tier, item);
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /** {@code true} when a static vanilla-tier tool already covers this tier. */
    public static boolean isVanillaTier(Tier tier) {
        return STATIC_BY_TIER.containsKey(tier);
    }

    /** Records an item registered by the dynamic layer. */
    public static void registerDynamic(Item item) {
        if (!DYNAMIC_TOOLS.contains(item)) {
            DYNAMIC_TOOLS.add(item);
        }
    }

    /** All statically registered tools (registry objects, safe before/after registration). */
    public static List<RegistryObject<Item>> staticTools() {
        return Collections.unmodifiableList(STATIC_TOOLS);
    }

    /** Items added by the dynamic layer so far. */
    public static List<Item> dynamicTools() {
        return Collections.unmodifiableList(DYNAMIC_TOOLS);
    }

    /**
     * Every Omni Tool, static first then dynamic. Used for the creative tab and the item tag.
     *
     * @return mutable-in-order list of items; only valid after registration completed
     */
    public static List<Item> allTools() {
        List<Item> all = new ArrayList<>(STATIC_TOOLS.size() + DYNAMIC_TOOLS.size());
        for (RegistryObject<Item> object : STATIC_TOOLS) {
            if (object.isPresent()) {
                all.add(object.get());
            }
        }
        all.addAll(DYNAMIC_TOOLS);
        return all;
    }

    /** Registry name of a tool, {@code null} when it is not registered (should not happen). */
    @Nullable
    public static net.minecraft.resources.ResourceLocation idOf(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    /**
     * Material descriptions of the statically registered tools, in registration order.
     * Data generation uses this to emit recipes, models, names and tags for the vanilla tiers.
     */
    public static List<OmniToolMaterial> staticMaterials() {
        return Collections.unmodifiableList(STATIC_MATERIALS);
    }
}
