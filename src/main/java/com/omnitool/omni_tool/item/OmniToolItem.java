package com.omnitool.omni_tool.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.omnitool.omni_tool.compat.EnchantmentCompatibility;
import com.omnitool.omni_tool.registry.OmniToolMaterial;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;

/**
 * The Omni Tool: one item that behaves like a pickaxe, an axe, a shovel and a sword at once.
 *
 * <h2>Why extend {@link DiggerItem}?</h2>
 * In 1.20.1 {@code DiggerItem} is the vanilla base class shared by pickaxe/axe/shovel. Extending it
 * gives, for free and with exact vanilla semantics:
 * <ul>
 *   <li>durability from the {@link Tier} - {@code TieredItem} applies
 *       {@code properties.defaultDurability(tier.getUses())} (requirement: reuse tier durability);</li>
 *   <li>enchantability from the tier - {@code getEnchantmentValue()} delegates to the tier;</li>
 *   <li>repair material from the tier - {@code isValidRepairItem} delegates to the tier;</li>
 *   <li>durability cost: {@code hurtEnemy} spends 2, {@code mineBlock} spends 1 for every block with
 *       a non-zero destroy speed. Both are inherited unchanged, because that is exactly the
 *       behaviour a multi tool should have.</li>
 * </ul>
 * Only the behaviours that genuinely differ for a multi tool are overridden: block-tag driven mining
 * speed, block-tag driven drop logic, and the attack attribute profile.
 *
 * <h2>Mining speed selection</h2>
 * The vanilla block tags {@code minecraft:mineable/pickaxe}, {@code minecraft:mineable/axe} and
 * {@code minecraft:mineable/shovel} decide which speed applies. Blocks that live in more than one
 * tag resolve as <b>pickaxe -&gt; axe -&gt; shovel</b>, matching the order of the tool heads on the
 * recipe icon. All three speeds already include the versatility penalty (see
 * {@link OmniToolMaterial}).
 *
 * <p>As in vanilla {@code DiggerItem#getDestroySpeed}, the tier level is <i>not</i> consulted for
 * speed: a too-weak tool still swings at full speed but does not get drops (see below). Blocks that
 * belong to none of the three tags fall back to the vanilla value (1.0, i.e. bare hands).
 *
 * <h2>Drop logic - two overloads on purpose</h2>
 * Forge routes block drops through
 * {@code ItemStack#isCorrectToolForDrops(BlockState)} -&gt;
 * {@code IForgeItem#isCorrectToolForDrops(ItemStack, BlockState)}, and {@code DiggerItem} overrides
 * that ItemStack sensitive variant with a check against its own single block tag. That is precisely
 * the assumption this mod has to break, so <b>both</b> overloads are overridden here:
 * <pre>
 *   isCorrectToolForDrops(ItemStack, BlockState)  // what the game actually calls
 *   isCorrectToolForDrops(BlockState)             // the vanilla signature, still used by other mods
 * </pre>
 * Both delegate to {@link #canHarvestWith(BlockState)}, which combines "is one of our three tags"
 * with {@link TierSortingRegistry#isCorrectTierForDrops}, Forge's tier check. Using Forge's helper
 * instead of hard coded {@code NEEDS_*_TOOL} comparisons means modded tiers registered through
 * {@code TierSortingRegistry} are ordered correctly, while unsorted tiers fall back to the vanilla
 * level comparison.
 *
 * <h2>Enchantments</h2>
 * Forge routes {@code Enchantment#canEnchant(ItemStack)} through
 * {@code IForgeItem#canApplyAtEnchantingTable(ItemStack, Enchantment)}. Because the Omni Tool is a
 * {@code DiggerItem}, {@code DIGGER} enchantments (Efficiency, Fortune, Silk Touch, ...) already
 * apply. {@link #canApplyAtEnchantingTable} additionally allows {@code WEAPON}, which enables
 * Sharpness, Smite, Bane of Arthropods, Looting, Fire Aspect and Knockback - i.e. the shared
 * pickaxe/axe/shovel/sword enchantments plus the weapon ones (requirement 6).
 */
public class OmniToolItem extends DiggerItem {

    /** Human readable name of the two attribute modifiers this tool contributes. */
    private static final String MODIFIER_NAME = "Omni Tool modifier";

    private final OmniToolMaterial omniMaterial;
    private final Multimap<Attribute, AttributeModifier> omniDefaultModifiers;

    /**
     * @param omniMaterial material description (tier plus derived, balanced speeds and damage)
     * @param properties   item properties; durability is applied by the super constructor from the tier
     */
    public OmniToolItem(OmniToolMaterial omniMaterial, Properties properties) {
        // DiggerItem(float attackDamageBaseline, float attackSpeedModifier, Tier tier,
        //             TagKey<Block> blocks, Properties properties)
        //
        // Note the exact semantics of the two float parameters (verified against the 1.20.1 sources):
        //   * attackDamageBaseline = first parameter + tier.getAttackDamageBonus(), and the mining
        //     `speed` field is taken from the TIER, not from the second parameter;
        //   * the second parameter is the ATTACK SPEED modifier (Operation.ADDITION).
        // We therefore pass the sword base damage (the tier bonus is added by the super constructor)
        // and the sword-derived attack speed modifier, which keeps the inherited fields coherent even
        // though `getDestroySpeed` / `getDefaultAttributeModifiers` are overridden below.
        super(OmniToolMaterial.SWORD_BASE_DAMAGE, OmniToolMaterial.SWORD_ATTACK_SPEED_MODIFIER,
                omniMaterial.tier(), BlockTags.MINEABLE_WITH_PICKAXE, properties);
        this.omniMaterial = omniMaterial;

        // The block tag passed to the super constructor is irrelevant: both drop and speed logic are
        // replaced. It is kept as the pickaxe tag so that any third party reading DiggerItem#blocks
        // sees a sensible value.
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
                MODIFIER_NAME, omniMaterial.attackDamage(), AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID,
                MODIFIER_NAME, omniMaterial.attackSpeedModifier(), AttributeModifier.Operation.ADDITION));
        this.omniDefaultModifiers = builder.build();
    }

    /** Material description this tool was built from. */
    public OmniToolMaterial getOmniMaterial() {
        return this.omniMaterial;
    }

    // -------------------------------------------------------------------------------------------
    // Mining
    // -------------------------------------------------------------------------------------------

    /**
     * Picks the mining speed that matches the block's tool tag, applying the "multi functional but
     * not specialised" penalty baked into {@link OmniToolMaterial}.
     */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return this.omniMaterial.pickaxeSpeed();
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return this.omniMaterial.axeSpeed();
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return this.omniMaterial.shovelSpeed();
        }
        return super.getDestroySpeed(stack, state);
    }

    /** {@code true} when the state belongs to one of the three block tags this tool covers. */
    public static boolean isOmniMineable(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    /**
     * Drop decision shared by both overloads: the block must be covered by one of the three tool
     * tags <b>and</b> the tier must be strong enough.
     *
     * <p>Blocks that do not require a correct tool (dirt, sand, ...) are unaffected: the game grants
     * their drops regardless, exactly as it does for a vanilla pickaxe.
     */
    public boolean canHarvestWith(BlockState state) {
        return isOmniMineable(state) && TierSortingRegistry.isCorrectTierForDrops(getTier(), state);
    }

    /** ItemStack sensitive variant - the one the game actually uses for block drops. */
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return canHarvestWith(state);
    }

    /**
     * Vanilla signature, kept for compatibility with third party callers.
     *
     * @deprecated the ItemStack sensitive variant above is what Forge routes drops through; this
     *             override exists so both entry points agree.
     */
    @Override
    @Deprecated
    public boolean isCorrectToolForDrops(BlockState state) {
        return canHarvestWith(state);
    }

    // -------------------------------------------------------------------------------------------
    // Combat
    // -------------------------------------------------------------------------------------------

    /**
     * Attack profile: sword damage formula with a slightly slower swing than a real sword. The whole
     * map is replaced for the main hand, so no modifier from the base class leaks in.
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.omniDefaultModifiers
                : super.getDefaultAttributeModifiers(slot);
    }

    // -------------------------------------------------------------------------------------------
    // Enchanting and naming
    // -------------------------------------------------------------------------------------------

    /**
     * Forge hook used by the enchanting table, the anvil, and by third party enchantment UIs that
     * go through the vanilla/Forge query path - notably
     * <a href="https://www.curseforge.com/minecraft/mc-mods/enchanting-infuser">Enchanting
     * Infuser</a>, whose {@code ForgeAbstractions#canApplyAtEnchantingTable} ends up here.
     *
     * <p>{@code DIGGER}, {@code BREAKABLE} and {@code VANISHABLE} enchantments are already accepted
     * by the {@code DiggerItem} default (which is consulted through {@code super}); the accepted set
     * is widened to include {@code WEAPON} so the tool can also take sword enchantments
     * (requirement 6).
     *
     * <p>The accepted set itself lives in
     * {@link com.omnitool.omni_tool.compat.EnchantmentCompatibility} so that the contract is shared
     * with the runtime self-check and with the unit tests instead of being hard coded here.
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return EnchantmentCompatibility.accepts(enchantment.category)
                || super.canApplyAtEnchantingTable(stack, enchantment);
    }

    /**
     * Display name.
     *
     * <p>Statically registered (vanilla tier) tools resolve through the normal language key
     * {@code item.omni_tool.omni_tool_<material>}. Tools discovered at runtime from a foreign
     * pickaxe have no generated language entry, so their name is composed from the source pickaxe's
     * own localised name: "Tin Pickaxe (Omni Tool)" / "锡镐（全能工具）".
     */
    @Override
    public Component getName(ItemStack stack) {
        String source = this.omniMaterial.sourcePickaxe();
        if (source != null) {
            return Component.translatable("item.omni_tool.dynamic_name", Component.translatable(source));
        }
        return super.getName(stack);
    }
}
