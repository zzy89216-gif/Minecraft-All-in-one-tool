package com.omnitool.omni_tool.registry;

import net.minecraft.world.item.Tier;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable description of one Omni Tool material (one "tier" -> one craftable Omni Tool).
 *
 * <p>Everything an {@link com.omnitool.omni_tool.item.OmniToolItem} needs is <b>derived from the
 * vanilla {@link Tier} object</b> that the source pickaxe already uses. That is what makes the
 * dynamic layer work: when another mod adds a "tin" material and a {@code tin_pickaxe} backed by
 * its own {@code Tier}, this class can describe a tin Omni Tool without knowing anything about
 * tin at compile time.
 *
 * <h2>Balance: "multi functional, not specialised"</h2>
 * A dedicated tool gets its full vanilla speed for one tool class. The Omni Tool trades raw
 * speed for versatility: each tool class runs at {@link #BALANCE_FACTOR} of what the dedicated
 * vanilla tool would be, never dropping below bare-hand speed (1.0).
 * <pre>
 *   pickaxe speed = max(1.0, tier.speed                    * 0.8)
 *   axe     speed = max(1.0, (tier.speed - AXE_OFFSET)      * 0.8)   // vanilla axe   = tier.speed - 3
 *   shovel  speed = max(1.0, (tier.speed - SHOVEL_OFFSET)   * 0.8)   // vanilla shovel= tier.speed - 2
 * </pre>
 * Example, diamond ({@code tier.speed == 8.0}): pickaxe 6.4, axe 4.0, shovel 4.8 - clearly
 * slower than the three dedicated diamond tools, clearly faster than bare hands.
 *
 * <h2>Attack profile</h2>
 * Damage uses the sword formula ({@code 3 + tier.attackDamageBonus}), attack speed is 1.4
 * instead of the sword's 1.6 (see {@link #SWORD_ATTACK_SPEED_MODIFIER}).
 *
 * @param id             registry path of the item, always prefixed with {@code omni_tool_}
 * @param tier           the vanilla/modded tier this tool is built from
 * @param materialKey    free-form key used for logging and language lookups
 * @param pickaxeSpeed   mining speed used for {@code mineable/pickaxe} blocks
 * @param axeSpeed       mining speed used for {@code mineable/axe} blocks
 * @param shovelSpeed    mining speed used for {@code mineable/shovel} blocks
 * @param attackDamage        attack damage in half-hearts (sword formula)
 * @param attackSpeedModifier {@code ADDITION} modifier for the attack speed attribute; see
 *                            {@link #attackSpeed()} for the resulting value
 * @param sourcePickaxe       the pickaxe this material was discovered from, {@code null} for the
 *                            statically registered vanilla tiers
 */
public record OmniToolMaterial(
        String id,
        Tier tier,
        String materialKey,
        float pickaxeSpeed,
        float axeSpeed,
        float shovelSpeed,
        float attackDamage,
        float attackSpeedModifier,
        @Nullable String sourcePickaxe) {

    /** Multiplier applied to every vanilla tool speed (see class javadoc). */
    public static final float BALANCE_FACTOR = 0.8F;

    /** Vanilla axe speed is {@code tier.getSpeed() - 3.0F}. */
    private static final float AXE_OFFSET = 3.0F;

    /** Vanilla shovel speed is {@code tier.getSpeed() - 2.0F}. */
    private static final float SHOVEL_OFFSET = 2.0F;

    /** Base attack damage of a sword before the tier bonus is added. */
    public static final float SWORD_BASE_DAMAGE = 3.0F;

    /**
     * Sword attack speed modifier is {@code -2.4} (final 1.6 attacks/second). The Omni Tool uses
     * {@code -2.6} -> 1.4, i.e. noticeably but not brutally slower than a dedicated sword.
     */
    public static final float SWORD_ATTACK_SPEED_MODIFIER = -2.6F;

    /** Minecraft's base attack speed attribute value. */
    public static final float BASE_ATTACK_SPEED = 4.0F;

    /**
     * Builds a material description for a vanilla-tier Omni Tool (the statically registered set).
     *
     * @param id          registry path, e.g. {@code omni_tool_diamond}
     * @param tier        the vanilla tier, e.g. {@link net.minecraft.world.item.Tiers#DIAMOND}
     * @param materialKey language key suffix, e.g. {@code diamond}
     */
    public static OmniToolMaterial ofVanilla(String id, Tier tier, String materialKey) {
        return derive(id, tier, materialKey, null);
    }

    /**
     * Builds a material description discovered at runtime from a foreign pickaxe.
     *
     * @param id            registry path of the new item, derived from the source pickaxe
     * @param tier          the tier the foreign pickaxe uses
     * @param materialKey   derived material name used for the display name fallback
     * @param sourcePickaxe registry name of the pickaxe this material came from (for diagnostics)
     */
    public static OmniToolMaterial ofDiscovered(String id, Tier tier, String materialKey,
                                                String sourcePickaxe) {
        return derive(id, tier, materialKey, sourcePickaxe);
    }

    private static OmniToolMaterial derive(String id, Tier tier, String materialKey,
                                           @Nullable String sourcePickaxe) {
        float base = tier.getSpeed();
        float pickaxe = balanced(base);
        float axe = balanced(base - AXE_OFFSET);
        float shovel = balanced(base - SHOVEL_OFFSET);
        float damage = SWORD_BASE_DAMAGE + tier.getAttackDamageBonus();
        return new OmniToolMaterial(id, tier, materialKey, pickaxe, axe, shovel, damage,
                SWORD_ATTACK_SPEED_MODIFIER, sourcePickaxe);
    }

    /**
     * Applies the versatility penalty while guaranteeing the result is never worse than an
     * empty hand. Values such as the vanilla wooden shovel speed (0.0) would otherwise clamp
     * to a negative number.
     */
    private static float balanced(float dedicatedSpeed) {
        return Math.max(1.0F, dedicatedSpeed * BALANCE_FACTOR);
    }

    /** Harvest level of the underlying tier ({@code 0} wood/gold, {@code 4} netherite). */
    public int harvestLevel() {
        return this.tier.getLevel();
    }

    /**
     * Resulting attack speed in attacks per second, i.e. the base attribute value plus
     * {@link #attackSpeedModifier()}. Diamond/netherite tools end up at 1.4 (a sword reaches 1.6).
     */
    public float attackSpeed() {
        return BASE_ATTACK_SPEED + this.attackSpeedModifier;
    }

    /** Durability of the tool, taken verbatim from the tier (requirement: reuse tier durability). */
    public int durability() {
        return this.tier.getUses();
    }

    /** Enchantability of the tool, taken from the tier. */
    public int enchantmentValue() {
        return this.tier.getEnchantmentValue();
    }

    /** {@code true} when this material was discovered from a non-vanilla pickaxe at runtime. */
    public boolean isDynamic() {
        return this.sourcePickaxe != null;
    }
}
