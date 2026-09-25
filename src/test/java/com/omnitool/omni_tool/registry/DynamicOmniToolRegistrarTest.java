package com.omnitool.omni_tool.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the name derivation used by the dynamic registration layer.
 *
 * <p>Deriving {@code tin} from {@code tin_pickaxe} is what lets a modded material be recognised
 * without any configuration, so the parsing rules are pinned down by tests. Only static, side-effect
 * free helpers are exercised; no registry or game bootstrap is involved.
 */
class DynamicOmniToolRegistrarTest {

    @Test
    @DisplayName("_pickaxe suffix is stripped from the source id")
    void stripsPickaxeSuffix() {
        assertEquals("tin", DynamicOmniToolRegistrar.materialKeyOf("tin_pickaxe"));
        assertEquals("mythril", DynamicOmniToolRegistrar.materialKeyOf("mythril_pickaxe"));
    }

    @Test
    @DisplayName("_pick suffix is stripped as well")
    void stripsPickSuffix() {
        assertEquals("steel", DynamicOmniToolRegistrar.materialKeyOf("steel_pick"));
    }

    @Test
    @DisplayName("ids without a pickaxe suffix are used unchanged")
    void keepsOtherNames() {
        assertEquals("hammer", DynamicOmniToolRegistrar.materialKeyOf("hammer"));
    }

    @Test
    @DisplayName("unsafe characters are sanitised into a valid registry path")
    void sanitisesUnsafeCharacters() {
        assertEquals("copper_tin", DynamicOmniToolRegistrar.sanitize("Copper Tin"));
        assertEquals("a_b_c", DynamicOmniToolRegistrar.sanitize("a+b=c"));
        assertEquals("tinkers_bronze", DynamicOmniToolRegistrar.sanitize("tinkers:bronze"));
    }

    @Test
    @DisplayName("a pickaxe whose whole name is the suffix still yields a usable key")
    void neverReturnsAnEmptyKey() {
        // "pickaxe" is not a valid material name on its own, but the derivation must not return "".
        assertEquals("pickaxe", DynamicOmniToolRegistrar.materialKeyOf("pickaxe"));
    }
}
