package hexicube.minecraft.AnvilEnchantWear;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;

public final class AnvilEnchantWear extends JavaPlugin implements Listener {
    // should only appear temporarily
    private final NamespacedKey repairKey = new NamespacedKey(this, "REPAIR");

    private static Random rng = new Random();

    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    /*private int xpToNextLevel(int level) {
        if (level < 0) return 0;
        if (level < 16) return 2 * level + 7;
        if (level < 31) return 5 * level - 38;
        return 9 * level - 158;
    }
    private int xpForLevelCumulative(int level) {
        if (level < 0) return 0;
        if (level < 17) return level * (6 + level); //L^2 + 6L
        if (level < 31) return ((5 * level * level) - (81 * level) + 720) / 2; // 2.5L^2 - 40.5L + 360
        return ((9 * level * level) - (325 * level) + 4440) / 2; // 4.5L^2 - 162.5L + 2220
    }

    private static Map<Enchantment, int[]> enchValues = new HashMap<Enchantment, int[]>();
    static {
        enchValues.put(Enchantment.DAMAGE_ALL, new int[] {1,2,3,4,5});
    }*/

    private int getEnchantWorth(ItemMeta meta) {
        Map<Enchantment, Integer> enchList = meta.getEnchants();

        // SIMPLIFIED VERSION
        // TODO: consider working via xp instead of levels?

        int cost = 0;
        for (Map.Entry<Enchantment, Integer> e : enchList.entrySet()) {
            if (!e.getKey().isCursed()) cost += e.getValue();
        }

        return cost;
    }
    private int getReduceOdds(int cost) {
        // config?
        return cost * 5;
    }
    private void dropEnchants(ItemMeta meta, HashMap<Enchantment, Integer> lostEnch) {
        Map<Enchantment, Integer> enchList = meta.getEnchants();
        ArrayList<Enchantment> keys = new ArrayList<>(enchList.keySet());
        while (true) {
            if (keys.isEmpty()) return;
            Enchantment chosen = keys.remove(rng.nextInt(keys.size()));
            if (chosen.isCursed()) continue;

            int curLevel = enchList.get(chosen);
            lostEnch.put(chosen, curLevel);

            meta.removeEnchant(chosen);
            if (curLevel > 1) meta.addEnchant(chosen, curLevel-1, true);

            if (rng.nextBoolean() || rng.nextBoolean()) return; // 75% to break out
        }
    }

    private static ArrayList<String> listToAList(List<String> data) {
        if (data == null) return new ArrayList<>();
        return new ArrayList<>(data);
    }

    @EventHandler
    public void onAnvilPrep(PrepareAnvilEvent event) {
        ItemStack[] inv = event.getInventory().getContents();
        if (inv[0] == null || inv[1] == null) return; // rename only (or other weirdness)
        if (inv[1].getType() == Material.ENCHANTED_BOOK) return; // enchanting, probably

        ItemStack result = event.getResult();
        boolean firstEnch = !inv[0].getEnchantments().isEmpty(), secondEnch = !inv[1].getEnchantments().isEmpty();

        if (firstEnch != secondEnch) { // only one enchanted - must be repair?
            if (result == null) {
                // too expensive probably - override max repair as it retriggers the event
                event.getInventory().setMaximumRepairCost(9999);
                return;
            }

            // work out a failure chance % (kept in 0-100 range for byte assign later)
            int cost = event.getInventory().getRepairCost();
            int odds = getReduceOdds(cost);
            if (odds < 0) odds = 0;
            if (odds > 100) odds = 100;

            // work out a new cost in levels
            ItemMeta m = result.getItemMeta();
            int levels = getEnchantWorth(m);

            // set the result data for later
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(repairKey, PersistentDataType.BYTE, (byte)odds);

            // show the player their odds
            ArrayList<String> lore = listToAList(m.getLore());
            if (odds < 20) lore.add(0, "§r§fFailure: §a" + (odds < 10 ? "0" : "") + odds + "%");
            else if (odds < 40) lore.add(0, "§r§fFailure: §e" + odds + "%");
            else if (odds < 80) lore.add(0, "§r§fFailure: §6" + odds + "%");
            else if (odds < 100) lore.add(0, "§r§fFailure: §c" + odds + "%");
            else lore.add(0, "§r§fFailure: §d100%");
            m.setLore(lore);

            // show results
            result.setItemMeta(m);
            event.setResult(result);
            event.getInventory().setRepairCost(levels);
        }
    }

    // TODO: expand this? not sure what reasonable max is
    private static String[] INTEGER_ROMAN = { "0", "I", "II", "III", "IV", "V" };

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;

        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        if (event.getSlot() != 2) return;

        //getServer().getLogger().info("[ANVIL_ENCH] Clicked on anvil out...");

        AnvilInventory inv = (AnvilInventory)event.getInventory();
        ItemStack item = inv.getItem(2);
        if (item != null && item.hasItemMeta()) {

            // work out the cost again, as a precaution
            ItemMeta m = item.getItemMeta();
            int levels = getEnchantWorth(m);

            // look for the data tag
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            Byte odds = pdc.get(repairKey, PersistentDataType.BYTE);
            if (odds == null) {
                // minor addition: if result unenchanted (or just cursed), keep forcing RepairCost to 0
                if (levels == 0) {
                    item = NMSItemStackHandler.resetRepairCost(item, this);
                    inv.setItem(2, item);
                }
            }
            else {
                // cancel immediately - uncancelled later on success, helps deal with errors
                event.setCancelled(true);

                // make sure space exists before continuing
                // could be possible to exploit otherwise, by shift-clicking to force a result with a packed inventory and then clicking if it didnt get reduced
                boolean success = false;
                HumanEntity who = event.getWhoClicked();
                if (event.isShiftClick()) {
                    if (who.getInventory().firstEmpty() != -1) success = true;
                }
                else {
                    if (who.getItemOnCursor().getType() == Material.AIR)
                        success = true;
                }
                if (!success) return;

                // remove enchantments on a failed RNG check
                HashMap<Enchantment, Integer> lostEnch = new HashMap<>();
                if (rng.nextInt(100) < odds) dropEnchants(m, lostEnch);

                // remove the tag and lore line (assumption: first line ours)
                pdc.remove(repairKey);
                ArrayList<String> lore = listToAList(m.getLore());
                if (lore.size() > 0) lore.remove(0);
                if (lore.size() == 0) lore = null;
                m.setLore(lore);

                // set it here, in case we need to use NMS
                item.setItemMeta(m);

                // above RNG check failed, let the player know what was removed
                if (!lostEnch.isEmpty()) {
                    who.sendMessage("[AnvilEnchWear] §cEnchantments lost!");
                    for (Map.Entry<Enchantment, Integer> entry : lostEnch.entrySet()) {
                        // get the name - this is lowercase
                        Enchantment ench = entry.getKey();
                        String[] nameParts = entry.getKey().getKey().getKey().split("_");
                        StringBuilder stringBuilder = new StringBuilder();
                        for (String part : nameParts) {
                            if (part.equals("of") || part.equals("the")) stringBuilder.append(part);
                            else stringBuilder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
                            stringBuilder.append(" ");
                        }
                        if (ench.getMaxLevel() != 1) {
                            int lvl = entry.getValue();
                            stringBuilder.append(INTEGER_ROMAN[lvl]).append(" ");
                            if (lvl > 1) stringBuilder.append("-> ").append(INTEGER_ROMAN[lvl - 1]);
                        }
                        // probably ok to leave trailing space?

                        who.sendMessage("[AnvilEnchWear]   §c" + stringBuilder.toString());
                    }
                    item = NMSItemStackHandler.resetRepairCost(item, this);
                }
                // TODO: config? or just always?
                // else who.sendMessage("[AnvilEnchWear] §aEnchantments preserved.");

                // set result and cost, uncancel event
                inv.setItem(2, item);
                event.setCancelled(false);
                inv.setRepairCost(levels);
            }
        }
    }
}