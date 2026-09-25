package com.omnitool.omni_tool.registry;

import com.mojang.logging.LogUtils;
import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.item.OmniToolItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Discovers pickaxe materials that this mod does not know statically and registers an Omni Tool
 * for each of them.
 *
 * <h2>Why here, and not at recipe time?</h2>
 * Forge freezes the item registry when the registration phase ends, so items <b>must</b> be created
 * while the {@link RegisterEvent} for {@code minecraft:item} is still running. Recipes, on the
 * other hand, only exist much later (datapack load). The two layers therefore meet in the middle:
 * <ul>
 *   <li>this class creates one Omni Tool per discovered {@link Tier} at registration time;</li>
 *   <li>{@link com.omnitool.omni_tool.recipe.OmniToolRecipeCloner} later clones the actual
 *       crafting recipes for those tools.</li>
 * </ul>
 *
 * <h2>Why {@link EventPriority#LOWEST}?</h2>
 * The handler runs after every other mod has registered its items, so the scan sees the complete
 * item list - including pickaxes added by mods that were loaded after this one.
 *
 * <h2>Safety</h2>
 * The whole scan is wrapped in a {@code try/catch(Throwable)}: a broken or unusual registry state
 * must never prevent the game from starting. Failures are logged and the vanilla six tiers keep
 * working. Candidates are collected in a first pass and registered in a second pass, because the
 * registry must not be modified while it is being iterated.
 */
@Mod.EventBusSubscriber(modid = OmniToolMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DynamicOmniToolRegistrar {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PICKAXE_SUFFIX = "_pickaxe";
    private static final String PICK_SUFFIX = "_pick";

    /** Tiers for which a dynamic tool was already created (guards repeated registration). */
    private static final Set<Tier> HANDLED_TIERS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private DynamicOmniToolRegistrar() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterItems(RegisterEvent event) {
        if (!Registries.ITEM.equals(event.getRegistryKey())) {
            return;
        }
        try {
            discoverAndRegister(event);
        } catch (Throwable throwable) {
            LOGGER.error("[OmniTool] Dynamic Omni Tool discovery failed; the six vanilla tiers are "
                    + "unaffected and the game will continue to load.", throwable);
        }
    }

    private static void discoverAndRegister(RegisterEvent event) {
        IForgeRegistry<Item> registry = event.getForgeRegistry();
        if (registry == null) {
            return;
        }

        Set<String> usedPaths = new HashSet<>();
        for (RegistryObject<Item> staticallyRegistered : ModItems.staticTools()) {
            usedPaths.add(staticallyRegistered.getId().getPath());
        }

        // ---- pass 1: collect candidates (never mutate the registry while iterating it) ----------
        List<Candidate> candidates = new ArrayList<>();
        Set<Tier> collected = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (Item item : registry) {
            if (!(item instanceof PickaxeItem pickaxe)) {
                continue;
            }
            Tier tier = pickaxe.getTier();
            if (tier == null) {
                continue;
            }
            if (ModItems.isVanillaTier(tier) || HANDLED_TIERS.contains(tier) || collected.contains(tier)) {
                // Already covered: vanilla tiers have hand written recipes via data generation.
                continue;
            }
            ResourceLocation sourceId = registry.getKey(item);
            if (sourceId == null) {
                continue;
            }

            String materialKey = materialKeyOf(sourceId.getPath());
            String path = uniquePath(ModItems.ID_PREFIX + materialKey, sourceId, usedPaths);

            candidates.add(new Candidate(tier, path, materialKey, pickaxe.getDescriptionId()));
            collected.add(tier);
        }

        // ---- pass 2: register ----------------------------------------------------------------
        for (Candidate candidate : candidates) {
            final OmniToolMaterial material = OmniToolMaterial.ofDiscovered(
                    candidate.path(), candidate.tier(), candidate.materialKey(), candidate.nameKey());
            final ResourceLocation id = new ResourceLocation(OmniToolMod.MOD_ID, candidate.path());

            Supplier<Item> factory = () -> {
                OmniToolItem created = new OmniToolItem(material, new Item.Properties());
                ModItems.registerDynamic(created);
                return created;
            };
            event.register(Registries.ITEM, id, factory);
            HANDLED_TIERS.add(candidate.tier());

            LOGGER.info("[OmniTool] Discovered modded material '{}' (tier level {}, speed {}) from {}; "
                            + "registered {}. Its recipe will be cloned once recipes are loaded.",
                    candidate.materialKey(), candidate.tier().getLevel(), candidate.tier().getSpeed(),
                    candidate.nameKey(), id);
        }

        if (!candidates.isEmpty()) {
            LOGGER.info("[OmniTool] Dynamic layer registered {} additional Omni Tool(s)", candidates.size());
        }
    }

    /**
     * Derives a material key from a pickaxe's registry path: {@code tin_pickaxe -> tin},
     * {@code steel_pick -> steel}, {@code hammer -> hammer}.
     */
    static String materialKeyOf(String pickaxePath) {
        String material = pickaxePath;
        if (material.endsWith(PICKAXE_SUFFIX)) {
            material = material.substring(0, material.length() - PICKAXE_SUFFIX.length());
        } else if (material.endsWith(PICK_SUFFIX)) {
            material = material.substring(0, material.length() - PICK_SUFFIX.length());
        }
        material = sanitize(material);
        return material.isEmpty() ? "unknown" : material;
    }

    /** Makes an arbitrary string safe for use in a registry path. */
    static String sanitize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_/.-]", "_");
    }

    /**
     * Avoids clashing with the static tools and with tools discovered earlier: a second material
     * called "tin" from another namespace becomes {@code omni_tool_<namespace>_tin}.
     */
    private static String uniquePath(String desiredPath, ResourceLocation sourceId, Set<String> usedPaths) {
        if (usedPaths.add(desiredPath)) {
            return desiredPath;
        }
        String namespaced = sanitize(sourceId.getNamespace()) + "_" + desiredPath;
        String candidate = namespaced;
        int suffix = 2;
        while (!usedPaths.add(candidate)) {
            candidate = namespaced + "_" + suffix++;
        }
        return candidate;
    }

    /** A tier that needs a dynamically registered Omni Tool. */
    private record Candidate(Tier tier, String path, String materialKey, String nameKey) {
    }
}
