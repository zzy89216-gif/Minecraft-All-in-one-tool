package com.omnitool.omni_tool.registry;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OmniToolMaterial}.
 *
 * <p>These tests never touch the game bootstrap: they build a hand written {@link Tier} and check the
 * arithmetic that the balance rules promise. That keeps them fast and runnable from a plain JVM
 * ({@code ./gradlew test}), while still pinning down the numbers that make the mod "multi functional
 * but not specialised".
 */
class OmniToolMaterialTest {

    /** A fictional "tin" tier: level 2, 250 uses, speed 5.0, +2 damage, enchantability 12. */
    private static final Tier TIN = new Tier() {
        @Override
        public int getUses() {
            return 250;
        }

        @Override
        public float getSpeed() {
            return 5.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 2.0F;
        }

        @Override
        public int getLevel() {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 12;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return null; // never called by this test
        }
    };

    @Test
    @DisplayName("mining speeds are 80% of the matching vanilla tool")
    void miningSpeedsAreBalanced() {
        OmniToolMaterial material = OmniToolMaterial.ofVanilla("omni_tool_tin", TIN, "tin");

        // vanilla pickaxe = tier speed                     -> 5.0  * 0.8 = 4.0
        assertEquals(4.0F, material.pickaxeSpeed(), 0.0001F);
        // vanilla axe     = tier speed - 3.0               -> 2.0  * 0.8 = 1.6
        assertEquals(1.6F, material.axeSpeed(), 0.0001F);
        // vanilla shovel  = tier speed - 2.0               -> 3.0  * 0.8 = 2.4
        assertEquals(2.4F, material.shovelSpeed(), 0.0001F);
    }

    @Test
    @DisplayName("speeds never drop below bare hand speed")
    void speedsAreFlooredAtBareHandSpeed() {
        // The wooden shovel has a dedicated speed of 0.0 and the wooden axe is negative.
        Tier woodenTier = new Tier() {
            @Override
            public int getUses() {
                return 59;
            }

            @Override
            public float getSpeed() {
                return 2.0F;
            }

            @Override
            public float getAttackDamageBonus() {
                return 0.0F;
            }

            @Override
            public int getLevel() {
                return 0;
            }

            @Override
            public int getEnchantmentValue() {
                return 15;
            }

            @Override
            public Ingredient getRepairIngredient() {
                return null;
            }
        };
        OmniToolMaterial material = OmniToolMaterial.ofVanilla("omni_tool_wood", woodenTier, "wood");

        assertEquals(1.0F, material.axeSpeed(), 0.0001F, "negative axe speed must clamp to 1.0");
        assertEquals(1.0F, material.shovelSpeed(), 0.0001F, "0.0 shovel speed must clamp to 1.0");
        assertEquals(1.6F, material.pickaxeSpeed(), 0.0001F);
    }

    @Test
    @DisplayName("attack damage follows the sword formula and the swing is slower than a sword")
    void attackProfileFollowsSwordRules() {
        OmniToolMaterial material = OmniToolMaterial.ofVanilla("omni_tool_tin", TIN, "tin");

        // sword formula: 3 + tier bonus
        assertEquals(5.0F, material.attackDamage(), 0.0001F);
        // sword final attack speed is 1.6; the omni tool is intentionally slower
        assertEquals(1.4F, material.attackSpeed(), 0.0001F);
        assertTrue(material.attackSpeed() < 4.0F + -2.4F, "must be slower than a sword");
    }

    @Test
    @DisplayName("tier durability, enchantability and harvest level are reused verbatim")
    void tierPropertiesAreReused() {
        OmniToolMaterial material = OmniToolMaterial.ofVanilla("omni_tool_tin", TIN, "tin");

        assertEquals(250, material.durability());
        assertEquals(12, material.enchantmentValue());
        assertEquals(2, material.harvestLevel());
    }

    @Test
    @DisplayName("discovered materials are flagged as dynamic, static ones are not")
    void dynamicFlagIsTracked() {
        OmniToolMaterial discovered = OmniToolMaterial.ofDiscovered("omni_tool_tin", TIN, "tin",
                "item.example.tin_pickaxe");
        OmniToolMaterial staticallyRegistered = OmniToolMaterial.ofVanilla("omni_tool_tin", TIN, "tin");

        assertTrue(discovered.isDynamic());
        assertFalse(staticallyRegistered.isDynamic());
        assertNotEquals(discovered, staticallyRegistered);
    }
}
