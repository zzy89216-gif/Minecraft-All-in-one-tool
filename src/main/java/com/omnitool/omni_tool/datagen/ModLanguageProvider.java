package com.omnitool.omni_tool.datagen;

import com.omnitool.omni_tool.OmniToolMod;
import com.omnitool.omni_tool.registry.ModItems;
import com.omnitool.omni_tool.registry.OmniToolMaterial;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Bilingual item names: {@code en_us.json} and {@code zh_cn.json}.
 *
 * <p>Naming follows the project convention "{material} Omni Tool" / "{材料}全能工具", for example
 * {@code Diamond Omni Tool} / {@code 钻石全能工具}.
 *
 * <p>Tools created by the dynamic layer cannot be translated here (they do not exist yet during data
 * generation), so {@code item.omni_tool.dynamic_name} provides the pattern those tools use:
 * "Tin Pickaxe (Omni Tool)" / "锡镐（全能工具）".
 */
public class ModLanguageProvider extends LanguageProvider {

    public static final String EN_US = "en_us";
    public static final String ZH_CN = "zh_cn";

    private static final String DYNAMIC_NAME_KEY = "item.omni_tool.dynamic_name";

    private final String locale;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, OmniToolMod.MOD_ID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        List<RegistryObject<Item>> tools = ModItems.staticTools();
        List<OmniToolMaterial> materials = ModItems.staticMaterials();
        for (int index = 0; index < tools.size() && index < materials.size(); index++) {
            if (tools.get(index).isPresent()) {
                // Note: Forge's LanguageProvider offers add(Item, String) for a concrete item and
                // addItem(Supplier<Item>, String) for a deferred one; a plain Item must use add(...).
                add(tools.get(index).get(), displayName(materials.get(index).materialKey()));
            }
        }

        add(DYNAMIC_NAME_KEY, isChinese() ? "%s（全能工具）" : "%s (Omni Tool)");
    }

    private boolean isChinese() {
        return ZH_CN.equals(this.locale);
    }

    /**
     * Display name for a material key.
     *
     * @param materialKey {@code wood, stone, iron, gold, diamond, netherite} or a discovered name
     */
    private String displayName(String materialKey) {
        boolean zh = isChinese();
        return switch (materialKey) {
            case "wood" -> zh ? "木全能工具" : "Wooden Omni Tool";
            case "stone" -> zh ? "石全能工具" : "Stone Omni Tool";
            case "iron" -> zh ? "铁全能工具" : "Iron Omni Tool";
            case "gold" -> zh ? "金全能工具" : "Golden Omni Tool";
            case "diamond" -> zh ? "钻石全能工具" : "Diamond Omni Tool";
            case "netherite" -> zh ? "下界合金全能工具" : "Netherite Omni Tool";
            default -> (zh ? "全能工具：" : "Omni Tool: ") + materialKey;
        };
    }
}
