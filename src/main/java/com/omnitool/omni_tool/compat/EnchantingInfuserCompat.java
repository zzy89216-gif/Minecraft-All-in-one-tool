package com.omnitool.omni_tool.compat;

import com.mojang.logging.LogUtils;
import com.omnitool.omni_tool.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility with <a href="https://www.curseforge.com/minecraft/mc-mods/enchanting-infuser">
 * Enchanting Infuser</a> (mod id {@code enchantinginfuser}).
 *
 * <h2>What Enchanting Infuser needs from us</h2>
 * Its {@code EnchantmentUtil#getAvailableEnchantments} decides which enchantments to offer for an
 * item with two questions, both of which must be answered by <i>our</i> item:
 * <pre>
 *   // 1. "may this enchantment go on this stack?"
 *   fuzs.enchantinginfuser.core.ForgeAbstractions#canApplyAtEnchantingTable
 *       -> Enchantment#canApplyAtEnchantingTable(ItemStack)        (Forge patch)
 *          -> ItemStack#canApplyAtEnchantingTable(Enchantment)     (Forge patch)
 *             -> IForgeItem#canApplyAtEnchantingTable(stack, enchantment)   <-- OUR OVERRIDE
 *
 *   // 2. "may the infuser modify this stack at all?"
 *   fuzs.enchantinginfuser.config.ServerConfig.ModifiableItems
 *       -> ItemStack#isEnchantable()
 * </pre>
 * Because the mod routes everything through the Forge hooks, an Omni Tool is compatible by
 * construction - its {@code canApplyAtEnchantingTable} override adds {@code WEAPON} to the
 * {@code DIGGER}/{@code BREAKABLE}/{@code VANISHABLE} default. Nothing has to be registered, and no
 * item tag or whitelist exists on their side that we would need to join.
 *
 * <p>Note that the {@code Fabric} module of Enchanting Infuser uses
 * {@code EnchantmentCategory#canEnchant(Item)} directly, which cannot be hooked per item. That path
 * does not affect Forge, and this mod is Forge only.
 *
 * <h2>What this class does</h2>
 * Relying on "it should work" is not something a maintainer can verify. When Enchanting Infuser is
 * present, {@link #runSelfCheck()} therefore replays both questions against the real enchantment
 * registry for every registered Omni Tool and logs a single, explicit result:
 * <ul>
 *   <li>every tool must report {@code isEnchantable() == true} (otherwise the infuser would refuse
 *       to modify it, and so would the vanilla anvil);</li>
 *   <li>for every registered enchantment, {@code canApplyAtEnchantingTable} must agree with
 *       {@link EnchantmentCompatibility#accepts(EnchantmentCategory)} - so a weapon enchantment must
 *       be accepted and an armour enchantment must still be rejected.</li>
 * </ul>
 * A mismatch is logged as a warning with the offending enchantment, which turns a silent
 * compatibility regression (caused by a Forge or mod update) into a visible one. The check is
 * advisory: it never throws and never changes behaviour.
 *
 * <p>No compile time dependency on Enchanting Infuser is needed - the check only uses vanilla and
 * Forge APIs, which is what keeps this mod loadable without it.
 */
public final class EnchantingInfuserCompat {

    /** Mod id of Enchanting Infuser. */
    public static final String INFUSER_MOD_ID = "enchantinginfuser";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many concrete mismatch examples are printed before the list is truncated. */
    private static final int MAX_REPORTED_PROBLEMS = 5;

    private EnchantingInfuserCompat() {
    }

    /** {@code true} when Enchanting Infuser is loaded in this instance. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(INFUSER_MOD_ID);
    }

    /**
     * Runs the compatibility self-check once during common setup.
     * Safe to call when Enchanting Infuser is absent (it just logs at debug level and returns).
     */
    public static void runSelfCheck() {
        if (!isLoaded()) {
            LOGGER.debug("[OmniTool] Enchanting Infuser not detected; skipping its compatibility self-check");
            return;
        }
        try {
            Report report = verify();
            if (report.mismatches() == 0) {
                LOGGER.info("[OmniTool] Enchanting Infuser compatibility verified: {} tool(s) x {} "
                                + "enchantment(s); accepted categories {}; weapon enchantments {} "
                                + "(all answered through canApplyAtEnchantingTable)",
                        report.tools(), report.enchantments(),
                        EnchantmentCompatibility.describeAcceptedCategories(), report.weaponEnchantments());
            } else {
                LOGGER.warn("[OmniTool] Enchanting Infuser compatibility self-check found {} "
                                + "mismatch(es); details: {}", report.mismatches(), report.problems());
                LOGGER.warn("[OmniTool] This usually means an enchantment contract change upstream. "
                        + "See HANDOFF.md section 'Enchanting Infuser compatibility'.");
            }
        } catch (Throwable throwable) {
            // Diagnostics must never be able to break startup.
            LOGGER.warn("[OmniTool] Enchanting Infuser compatibility self-check could not run", throwable);
        }
    }

    /**
     * Replays Enchanting Infuser's two questions for every Omni Tool.
     *
     * @return a report; never {@code null}
     */
    static Report verify() {
        List<String> problems = new ArrayList<>();
        int tools = 0;
        int enchantments = 0;
        int weaponEnchantments = 0;
        int mismatches = 0;

        for (Item item : ModItems.allTools()) {
            ItemStack stack = item.getDefaultInstance();
            tools++;

            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.isEnchantable()) {
                mismatches++;
                addProblem(problems, "tool " + nameOf(item) + " reports isEnchantable() == false; "
                        + "Enchanting Infuser would refuse to modify it");
            }

            for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
                enchantments++;
                EnchantmentCategory category = enchantment.category;
                if (category == EnchantmentCategory.WEAPON) {
                    weaponEnchantments++;
                }
                boolean expected = EnchantmentCompatibility.accepts(category);
                boolean actual = enchantment.canApplyAtEnchantingTable(stack);
                if (expected != actual) {
                    mismatches++;
                    addProblem(problems, nameOf(item) + " + " + nameOf(enchantment)
                            + " (category " + category + "): expected " + expected + " but got " + actual);
                }
            }
        }
        return new Report(tools, enchantments, weaponEnchantments, mismatches, problems);
    }

    private static void addProblem(List<String> problems, String problem) {
        if (problems.size() < MAX_REPORTED_PROBLEMS) {
            problems.add(problem);
        }
    }

    private static String nameOf(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? item.toString() : key.toString();
    }

    private static String nameOf(Enchantment enchantment) {
        ResourceLocation key = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
        return key == null ? enchantment.toString() : key.toString();
    }

    /**
     * Outcome of the self-check.
     *
     * @param tools               number of Omni Tools inspected
     * @param enchantments        number of enchantment/tool combinations tested
     * @param weaponEnchantments  number of weapon enchantments seen in the registry
     * @param mismatches          number of contract violations found
     * @param problems            up to {@link #MAX_REPORTED_PROBLEMS} human readable examples
     */
    record Report(int tools, int enchantments, int weaponEnchantments, int mismatches,
                  List<String> problems) {
    }
}
