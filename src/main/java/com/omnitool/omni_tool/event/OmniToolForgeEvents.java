package com.omnitool.omni_tool.event;

import com.mojang.logging.LogUtils;
import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.recipe.OmniToolRecipeCloner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server side wiring for the dynamic recipe layer.
 *
 * <h2>Which event, and why not {@code RecipesUpdatedEvent}?</h2>
 * The requirements suggest {@code RecipesUpdatedEvent}, but that event is declared in
 * {@code net.minecraftforge.client.event} and is only ever posted on the <b>logical client</b>.
 * Cloning there would give a single-player only feature: a dedicated server would serve the plain
 * pickaxe recipes and no client would ever see the Omni Tool recipes.
 *
 * <p>{@link OnDatapackSyncEvent} is the correct hook, and the Forge patch for
 * {@code PlayerList} proves it: the event is posted immediately <i>before</i> the server builds the
 * {@code ClientboundUpdateRecipesPacket}.
 * <pre>
 *   // PlayerList#placeNewPlayer           (player joins)
 *   MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(this, player));
 *   connection.send(new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes()));
 *
 *   // PlayerList#reloadResources          (/reload)
 *   MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(this, null));
 *   ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(...);
 * </pre>
 * Injecting the cloned recipes inside this event therefore means the server's own recipe manager
 * holds them, and every client - single player, LAN and dedicated - receives them through the
 * vanilla synchronisation packet. No client side cloning is required.
 *
 * <p>{@code replaceRecipes} rebuilds the internal lookup tables, so the newly added recipes are
 * immediately usable by the crafting grid, the recipe book and by other mods (JEI, ...).
 */
@Mod.EventBusSubscriber(modid = OmniToolMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OmniToolForgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    private OmniToolForgeEvents() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // Should not happen: the event is only posted by a running server.
            return;
        }
        try {
            injectClonedRecipes(server);
        } catch (Throwable throwable) {
            // A failure here must never disconnect players or abort the sync: the vanilla recipes
            // (including the six generated Omni Tools) stay intact.
            LOGGER.error("[OmniTool] Dynamic recipe injection failed; continuing with the recipes "
                    + "that are already loaded.", throwable);
        }
    }

    private static void injectClonedRecipes(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        Collection<Recipe<?>> existing = recipeManager.getRecipes();
        List<Recipe<?>> clones = OmniToolRecipeCloner.clonePickaxeRecipes(existing, server.registryAccess());
        if (clones.isEmpty()) {
            return;
        }

        List<Recipe<?>> merged = new ArrayList<>(existing.size() + clones.size());
        merged.addAll(existing);
        merged.addAll(clones);
        recipeManager.replaceRecipes(merged);

        LOGGER.info("[OmniTool] Added {} dynamically cloned recipe(s); the recipe manager now holds {} recipes",
                clones.size(), merged.size());
    }
}
