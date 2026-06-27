package dev.rudakov.rstonecutter;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.art.Art;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PaintingMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class RStonecutter extends JavaPlugin {

    private final Logger log = getLogger();
    private List<RecipeEntry> recipes = new ArrayList<>();
    private StonecutterHandler handler;

    // -------------------------------------------------------
    //  Маппинги дерева
    // -------------------------------------------------------

    private static final String[] WOOD_TYPES = {
        "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
        "MANGROVE", "CHERRY", "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED"
    };

    public String getWoodFamilyPublic(Material mat) {
        String name = mat.name();
        for (String wt : WOOD_TYPES) {
            if (name.contains(wt)) return wt;
        }
        return null;
    }

    public Material woodTargetPublic(String family, String target) {
        return switch (target) {
            case "planks"         -> safeMat(family + "_PLANKS");
            case "slab"           -> safeMat(family + "_SLAB");
            case "stairs"         -> safeMat(family + "_STAIRS");
            case "button"         -> safeMat(family + "_BUTTON");
            case "fence"          -> safeMat(family + "_FENCE");
            case "fence_gate"     -> safeMat(family + "_FENCE_GATE");
            case "pressure_plate" -> safeMat(family + "_PRESSURE_PLATE");
            case "trapdoor"       -> safeMat(family + "_TRAPDOOR");
            case "stripped_wood"  -> safeMat("STRIPPED_" + family + "_WOOD");
            default -> null;
        };
    }

    // -------------------------------------------------------
    //  Маппинги меди
    // -------------------------------------------------------

    private int copperOxidationLevel(Material mat) {
        String name = mat.name();
        if (name.startsWith("WAXED_")) name = name.substring(6);
        if (name.startsWith("OXIDIZED_"))  return 3;
        if (name.startsWith("WEATHERED_")) return 2;
        if (name.startsWith("EXPOSED_"))   return 1;
        return 0;
    }

    private String copperOxPrefix(int level) {
        return switch (level) {
            case 1 -> "EXPOSED_";
            case 2 -> "WEATHERED_";
            case 3 -> "OXIDIZED_";
            default -> "";
        };
    }

    private boolean isWaxed(Material mat) {
        return mat.name().startsWith("WAXED_");
    }

    public Material copperTargetPublic(Material inputMat, String target) {
        int level = copperOxidationLevel(inputMat);
        boolean waxed = isWaxed(inputMat);
        String ox  = copperOxPrefix(level);
        String wax = waxed ? "WAXED_" : "";

        return switch (target) {
            case "chiseled"      -> safeMat(wax + ox + "CHISELED_COPPER");
            case "door"          -> safeMat(wax + ox + "COPPER_DOOR");
            case "grate"         -> safeMat(wax + ox + "COPPER_GRATE");
            case "trapdoor"      -> safeMat(wax + ox + "COPPER_TRAPDOOR");
            case "carved"        -> safeMat(wax + ox + "CHISELED_COPPER");
            case "carved_slab"   -> safeMat(wax + ox + "CUT_COPPER_SLAB");
            case "carved_stairs" -> safeMat(wax + ox + "CUT_COPPER_STAIRS");
            case "dewax_block"   -> safeMat(ox + "COPPER_BLOCK");
            case "dewax_slab"    -> safeMat(ox + "CUT_COPPER_SLAB");
            case "dewax_stairs"  -> safeMat(ox + "CUT_COPPER_STAIRS");
            default -> null;
        };
    }

    // -------------------------------------------------------
    //  Картины
    // -------------------------------------------------------

    public ItemStack buildPaintingItemStack(String variantKey, int amount) {
        ItemStack item = new ItemStack(Material.PAINTING, amount);
        var meta = item.getItemMeta();
        if (!(meta instanceof PaintingMeta pm)) return null;

        NamespacedKey key = NamespacedKey.fromString(variantKey);
        if (key == null) return null;

        Art art = Registry.ART.get(key);
        if (art == null) {
            log.warning("Неизвестный вариант картины: " + variantKey);
            return null;
        }
        pm.setArt(art, true);
        item.setItemMeta(pm);
        return item;
    }

    // -------------------------------------------------------
    //  Утилиты
    // -------------------------------------------------------

    private Material safeMat(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<RecipeEntry> getRecipes() {
        return recipes;
    }

    // -------------------------------------------------------
    //  Жизненный цикл
    // -------------------------------------------------------

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadRecipes();

        handler = new StonecutterHandler(this);
        handler.registerAll();
        Bukkit.getPluginManager().registerEvents(handler, this);

        Objects.requireNonNull(getCommand("rsc-reload")).setExecutor((sender, cmd, label, args) -> {
            if (!sender.hasPermission("rstonecutter.reload")) {
                sender.sendMessage("§cНет прав: rstonecutter.reload");
                return true;
            }
            reloadConfig();
            loadRecipes();
            handler.registerAll();
            sender.sendMessage("§a[r.stonecutter] Перезагружено! (" + recipes.size() + " рецептов)");
            return true;
        });

        log.info("Включён. Рецептов в конфиге: " + recipes.size());
    }

    @Override
    public void onDisable() {
        if (handler != null) handler.unregisterAll();
        log.info("Отключён.");
    }

    // -------------------------------------------------------
    //  Загрузка рецептов
    // -------------------------------------------------------

    private void loadRecipes() {
        recipes.clear();
        FileConfiguration cfg = getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("recipes");
        if (sec == null) {
            log.warning("Раздел 'recipes' не найден в config.yml!");
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(key);
            if (r == null) continue;
            if (!r.getBoolean("enabled", true)) continue;

            RecipeEntry entry = new RecipeEntry();
            entry.id              = key;
            entry.inputItem       = r.getString("input");
            entry.inputTag        = r.getString("tag");
            entry.outputItem      = r.getString("output", "minecraft:air");
            entry.amount          = r.getInt("amount", 1);
            entry.inputCount      = r.getInt("input-count", 1);
            entry.dynamicWood     = r.getBoolean("dynamic_wood", false);
            entry.woodTarget      = r.getString("wood_target");
            entry.dynamicCopper   = r.getBoolean("dynamic_copper", false);
            entry.copperTargetKey = r.getString("copper_target");
            entry.paintingVariant = r.getString("painting_variant");
            entry.excludeInput    = r.getString("exclude_input");
            recipes.add(entry);
        }
        log.info("Загружено рецептов: " + recipes.size());
    }

    // -------------------------------------------------------
    //  Модель рецепта
    // -------------------------------------------------------

    public static class RecipeEntry {
        public String id;
        public String inputItem;
        public String inputTag;
        public String excludeInput;
        public String outputItem;
        public int    amount;
        public int    inputCount;
        public boolean dynamicWood;
        public String woodTarget;
        public boolean dynamicCopper;
        public String copperTargetKey;
        public String paintingVariant;
    }
}
