package com.omnitool.omni_tool.compat;

import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the enchantment contract that Enchanting Infuser (and the enchanting table, and the
 * anvil) rely on when they ask "may this enchantment go on an Omni Tool?".
 *
 * <p>These tests only touch the {@link EnchantmentCategory} enum, so they run in a plain JVM
 * without the game bootstrap. They exist because the accepted set is what makes the multi tool
 * behave like all four tool classes at once - widening it by accident (for example by returning
 * {@code true} for everything) would silently let armour enchantments onto a pickaxe.
 */
class EnchantmentCompatibilityTest {

    @Test
    @DisplayName("digger enchantments are accepted (pickaxe/axe/shovel set)")
    void acceptsDiggerEnchantments() {
        assertTrue(EnchantmentCompatibility.accepts(EnchantmentCategory.DIGGER));
    }

    @Test
    @DisplayName("weapon enchantments are accepted - this is what makes it a sword too")
    void acceptsWeaponEnchantments() {
        assertTrue(EnchantmentCompatibility.accepts(EnchantmentCategory.WEAPON));
    }

    @Test
    @DisplayName("durability and curse enchantments remain accepted")
    void acceptsDurabilityAndCurseEnchantments() {
        assertTrue(EnchantmentCompatibility.accepts(EnchantmentCategory.BREAKABLE));
        assertTrue(EnchantmentCompatibility.accepts(EnchantmentCategory.VANISHABLE));
    }

    @Test
    @DisplayName("armour, bow, crossbow, trident, fishing rod and wearable stay rejected")
    void rejectsUnrelatedCategories() {
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.ARMOR));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.ARMOR_HEAD));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.ARMOR_CHEST));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.ARMOR_LEGS));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.ARMOR_FEET));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.BOW));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.CROSSBOW));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.TRIDENT));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.FISHING_ROD));
        assertFalse(EnchantmentCompatibility.accepts(EnchantmentCategory.WEARABLE));
    }

    @Test
    @DisplayName("a null category is rejected instead of throwing")
    void rejectsNullCategory() {
        assertFalse(EnchantmentCompatibility.accepts(null));
    }

    @ParameterizedTest
    @EnumSource(EnchantmentCategory.class)
    @DisplayName("every category is answered deterministically and only the four are accepted")
    void everyCategoryIsClassified(EnchantmentCategory category) {
        boolean expected = category == EnchantmentCategory.DIGGER
                || category == EnchantmentCategory.WEAPON
                || category == EnchantmentCategory.BREAKABLE
                || category == EnchantmentCategory.VANISHABLE;
        assertEquals(expected, EnchantmentCompatibility.accepts(category),
                "unexpected verdict for category " + category);
    }

    @Test
    @DisplayName("exactly four categories are accepted, no more and no less")
    void acceptedSetHasExpectedSize() {
        assertEquals(4, EnchantmentCompatibility.acceptedCategories().size());
        assertTrue(EnchantmentCompatibility.describeAcceptedCategories().contains("WEAPON"));
    }
}
