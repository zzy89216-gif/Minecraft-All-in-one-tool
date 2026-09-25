package com.omnitool.omni_tool.compat;

import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The single source of truth for "which enchantments does an Omni Tool accept?".
 *
 * <h2>Why this class exists</h2>
 * An Omni Tool is a {@link net.minecraft.world.item.DiggerItem}, not a
 * {@link net.minecraft.world.item.SwordItem}, so out of the box it only accepts the
 * {@code DIGGER}, {@code BREAKABLE} and {@code VANISHABLE} categories. The mod deliberately widens
 * that to include the {@code WEAPON} category so a single item can carry the enchantments of all
 * four tool classes it replaces.
 *
 * <p>That decision is a <b>contract</b> other mods depend on: anything that asks "can this
 * enchantment go on this stack?" - the enchanting table, the anvil, and third party enchantment
 * UIs such as Enchanting Infuser - must get the same answer. The contract therefore lives here, in
 * one place, instead of being buried inside the item class:
 * <ul>
 *   <li>{@link com.omnitool.omni_tool.item.OmniToolItem#canApplyAtEnchantingTable} delegates to
 *       {@link #accepts(EnchantmentCategory)};</li>
 *   <li>{@link EnchantingInfuserCompat} verifies the contract against the real registry at startup;</li>
 *   <li>{@code EnchantmentCompatibilityTest} pins it down as a unit test.</li>
 * </ul>
 *
 * <h2>The four accepted categories</h2>
 * <table>
 *   <tr><td>{@code DIGGER}</td><td>Efficiency, Silk Touch, Fortune - the pickaxe/axe/shovel set</td></tr>
 *   <tr><td>{@code WEAPON}</td><td>Sharpness, Smite, Bane of Arthropods, Looting, Fire Aspect,
 *       Knockback, Sweeping Edge</td></tr>
 *   <tr><td>{@code BREAKABLE}</td><td>Unbreaking, Mending</td></tr>
 *   <tr><td>{@code VANISHABLE}</td><td>Curse of Vanishing</td></tr>
 * </table>
 * Everything else (armour, bow, crossbow, trident, fishing rod, wearable) is rejected; the
 * {@link #accepts(EnchantmentCategory)} contract is what keeps that rejection intact.
 */
public final class EnchantmentCompatibility {

    /** Categories every Omni Tool accepts, in a deterministic order for logging and tests. */
    private static final Set<EnchantmentCategory> ACCEPTED_CATEGORIES = Collections.unmodifiableSet(
            EnumSet.of(
                    EnchantmentCategory.DIGGER,
                    EnchantmentCategory.WEAPON,
                    EnchantmentCategory.BREAKABLE,
                    EnchantmentCategory.VANISHABLE));

    private EnchantmentCompatibility() {
    }

    /**
     * @param category the category of the enchantment being tested, may be {@code null}
     * @return {@code true} when an Omni Tool accepts enchantments of this category
     */
    public static boolean accepts(EnchantmentCategory category) {
        return category != null && ACCEPTED_CATEGORIES.contains(category);
    }

    /** Immutable view of the accepted categories (used by logging and by the self-check). */
    public static Set<EnchantmentCategory> acceptedCategories() {
        return ACCEPTED_CATEGORIES;
    }

    /** Human readable form, e.g. {@code [DIGGER, WEAPON, BREAKABLE, VANISHABLE]}. */
    public static String describeAcceptedCategories() {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (EnchantmentCategory category : ACCEPTED_CATEGORIES) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(category.name());
            first = false;
        }
        return builder.append(']').toString();
    }
}
