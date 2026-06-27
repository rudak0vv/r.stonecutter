package dev.rudakov.rstonecutter;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

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
    //  Регистрация / снятие рецептов
    // -------------------------------------------------------

    public void registerAll() {
        unregisterAll();
        for (RStonecutter.RecipeEntry entry : plugin.getRecipes()) {
            registerEntry(entry);
        }
        log.info("Зарегистрировано рецептов: " + registeredKeys.size());
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

        // Используем plugin (JavaPlugin) явно — правильная сигнатура NamespacedKey
        NamespacedKey key = new NamespacedKey(plugin, "rsc" + (++counter));
        StonecuttingRecipe recipe = new StonecuttingRecipe(
            key,
            result,
            new RecipeChoice.MaterialChoice(inputMat)
        );
        Bukkit.addRecipe(recipe);
        registeredKeys.add(key);
    }

    // -------------------------------------------------------
    //  Вычисление результата
    // -------------------------------------------------------

    private ItemStack resolveResult(RStonecutter.RecipeEntry entry, Material inputMat) {
        if (entry.paintingVariant != null) {
            return plugin.buildPaintingItemStack(entry.paintingVariant, entry.amount);
        }
        if (entry.dynamicWood && entry.woodTarget != null) {
            String family = plugin.getWoodFamilyPublic(inputMat);
            if (family == null) return null;
            Material out = plugin.woodTargetPublic(family, entry.woodTarget);
            return out != null ? new ItemStack(out, entry.amount) : null;
        }
        if (entry.dynamicCopper && entry.copperTargetKey != null) {
            Material out = plugin.copperTargetPublic(inputMat, entry.copperTargetKey);
            return out != null ? new ItemStack(out, entry.amount) : null;
        }
        Material out = Material.matchMaterial(entry.outputItem);
        return out != null ? new ItemStack(out, entry.amount) : null;
    }

    // -------------------------------------------------------
    //  Тег → список материалов
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
