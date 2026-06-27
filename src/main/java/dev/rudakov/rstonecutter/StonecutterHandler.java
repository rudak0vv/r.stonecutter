package dev.rudakov.rstonecutter;

import org.bukkit.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Регистрирует все рецепты камнереза в Bukkit и слушает клики в инвентаре.
 */
public class StonecutterHandler implements Listener {

    private final RStonecutter plugin;
    private final Logger log;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();
    private int counter = 0;

    public StonecutterHandler(RStonecutter plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    // -------------------------------------------------------
    //  Регистрация
    // -------------------------------------------------------

    public void registerAll() {
        unregisterAll();
        for (RStonecutter.RecipeEntry entry : plugin.getRecipes()) {
            registerEntry(entry);
        }
        log.info("Зарегистрировано в Bukkit рецептов: " + registeredKeys.size());
    }

    public void unregisterAll() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
        counter = 0;
    }

    private void registerEntry(RStonecutter.RecipeEntry entry) {
        if (entry.inputTag != null) {
            // Expand тег до всех Material-ов
            for (Material inputMat : resolveMaterialsFromTag(entry.inputTag)) {
                if (entry.excludeInput != null) {
                    Material excl = Material.matchMaterial(entry.excludeInput);
                    if (excl == inputMat) continue;
                }
                registerForMaterial(entry, inputMat);
            }
        } else if (entry.inputItem != null) {
            Material inputMat = Material.matchMaterial(entry.inputItem);
            if (inputMat != null) registerForMaterial(entry, inputMat);
        }
    }

    private void registerForMaterial(RStonecutter.RecipeEntry entry, Material inputMat) {
        ItemStack result = resolveResult(entry, inputMat);
        if (result == null || result.getType() == Material.AIR) return;

        NamespacedKey key = new NamespacedKey(plugin, "rsc_" + (++counter));
        StonecuttingRecipe recipe = new StonecuttingRecipe(
            key, result,
            new RecipeChoice.MaterialChoice(inputMat)
        );
        Bukkit.addRecipe(recipe);
        registeredKeys.add(key);
    }

    // -------------------------------------------------------
    //  Вычисление результата
    // -------------------------------------------------------

    private ItemStack resolveResult(RStonecutter.RecipeEntry entry, Material inputMat) {
        // Картина
        if (entry.paintingVariant != null) {
            return plugin.buildPaintingItemStack(entry.paintingVariant, entry.amount);
        }
        // Динамическое дерево
        if (entry.dynamicWood && entry.woodTarget != null) {
            String family = plugin.getWoodFamilyPublic(inputMat);
            if (family == null) return null;
            Material out = plugin.woodTargetPublic(family, entry.woodTarget);
            return out != null ? new ItemStack(out, entry.amount) : null;
        }
        // Динамическая медь
        if (entry.dynamicCopper && entry.copperTargetKey != null) {
            Material out = plugin.copperTargetPublic(inputMat, entry.copperTargetKey);
            return out != null ? new ItemStack(out, entry.amount) : null;
        }
        // Статический
        Material out = Material.matchMaterial(entry.outputItem);
        return out != null ? new ItemStack(out, entry.amount) : null;
    }

    // -------------------------------------------------------
    //  Теги
    // -------------------------------------------------------

    private Set<Material> resolveMaterialsFromTag(String tagStr) {
        Set<Material> result = new HashSet<>();
        NamespacedKey key = NamespacedKey.fromString(tagStr);
        if (key == null) return result;

        Tag<Material> blockTag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
        if (blockTag != null) result.addAll(blockTag.getValues());

        Tag<Material> itemTag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        if (itemTag != null) result.addAll(itemTag.getValues());

        return result;
    }
}
