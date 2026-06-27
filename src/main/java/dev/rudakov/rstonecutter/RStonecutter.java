package dev.rudakov.rstonecutter;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PaintingMeta;
import org.bukkit.art.Art;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
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
            case "planks"         -> mat(family + "_PLANKS");
            case "slab"           -> mat(family + "_SLAB");
            case "stairs"         -> mat(family + "_STAIRS");
            case "button"         -> mat(family + "_BUTTON");
            case "fence"          -> mat(family + "_FENCE");
            case "fence_gate"     -> mat(family + "_FENCE_GATE");
            case "pressure_plate" -> mat(family + "_PRESSURE_PLATE");
            case "trapdoor"       -> mat(family + "_TRAPDOOR");
            case "stripped_wood"  -> mat("STRIPPED_" + family + "_WOOD");
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
        // базовая медь (no prefix)
        if (name.startsWith("COPPER_") || name.startsWith("CUT_COPPER")
                || name.startsWith("CHISELED_COPPER")) return 0;
        return -1;
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
        if (level < 0) return null;
        boolean waxed = isWaxed(inputMat);
        String ox  = copperOxPrefix(level);
        String wax = waxed ? "WAXED_" : "";

        return switch (target) {
            case "chiseled"      -> mat(wax + ox + "CHISELED_COPPER");
            case "door"          -> mat(wax + ox + "COPPER_DOOR");
            case "grate"         -> mat(wax + ox + "COPPER_GRATE");
            case "trapdoor"      -> mat(wax + ox + "COPPER_TRAPDOOR");
            case "carved"        -> mat(wax + ox + "CHISELED_COPPER");
            case "carved_slab"   -> mat(wax + ox + "CUT_COPPER_SLAB");
            case "carved_stairs" -> mat(wax + ox + "CUT_COPPER_STAIRS");
            // Снять воск — тот же уровень без WAXED_
            case "dewax_block"   -> mat(ox + "COPPER_BLOCK");
            case "dewax_slab"    -> mat(ox + "CUT_COPPER_SLAB");
            case "dewax_stairs"  -> mat(ox + "CUT_COPPER_STAIRS");
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

    private Material mat(String name) {
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
    //  Жизненный цикл плагина
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
            sender.sendMessage("§a[r.stonecutter] Рецепты перезагружены! (" + recipes.size() + " рецептов)");
            return true;
        });

        log.info("r.stonecutter включён. Загружено рецептов из конфига: " + recipes.size());
    }

    @Override
    public void onDisable() {
        if (handler != null) handler.unregisterAll();
        log.info("r.stonecutter отключён.");
    }

    // -------------------------------------------------------
    //  Загрузка рецептов из конфига
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
            entry.inputItem       = r.getString("input", null);
            entry.inputTag        = r.getString("tag", null);
            entry.outputItem      = r.getString("output", "minecraft:air");
            entry.amount          = r.getInt("amount", 1);
            entry.inputCount      = r.getInt("input-count", 1);
            entry.dynamicWood     = r.getBoolean("dynamic_wood", false);
            entry.woodTarget      = r.getString("wood_target", null);
            entry.dynamicCopper   = r.getBoolean("dynamic_copper", false);
            entry.copperTargetKey = r.getString("copper_target", null);
            entry.paintingVariant = r.getString("painting_variant", null);
            entry.excludeInput    = r.getString("exclude_input", null);
            recipes.add(entry);
        }
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
