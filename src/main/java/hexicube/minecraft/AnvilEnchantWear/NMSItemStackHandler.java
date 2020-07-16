package hexicube.minecraft.AnvilEnchantWear;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

final class NMSItemStackHandler {
    private static boolean DIRECT_ISSUE = false; // test

    private static boolean REFLECT_ISSUE = false;
    private static Method REFLECT_COPYBUKKIT = null;
    private static Method REFLECT_GETTAG = null;
    private static Method REFLECT_SETINT = null;
    private static Method REFLECT_BUKKITMIRROR = null;

    static ItemStack resetRepairCost(ItemStack input, JavaPlugin plugin) {
        // attempt to use the known version
        if (!DIRECT_ISSUE) {
            try {
                net.minecraft.server.v1_16_R1.ItemStack nmsStack = net.minecraft.server.v1_16_R1.ItemStack.fromBukkitCopy(input);
                net.minecraft.server.v1_16_R1.NBTTagCompound tags = nmsStack.getOrCreateTag();
                tags.setInt("RepairCost", 0);
                nmsStack.setTag(tags);
                return nmsStack.asBukkitMirror();
            } catch (Exception e) {
                DIRECT_ISSUE = true;
                plugin.getServer().getLogger().log(Level.WARNING, "[AnvilEnchWear] Server is not running 1.16.1, RepairCost will be reset via Reflection instead");
            }
        }
        // attempt to use the detected version
        if (!REFLECT_ISSUE) {
            try {
                if (REFLECT_COPYBUKKIT == null) {
                    plugin.getServer().getLogger().finest("First time reflecting, checking all package names...");
                    // need to find it
                    for (Package p : Package.getPackages()) {
                        String name = p.getName().trim();
                        //plugin.getServer().getLogger().info(name);
                        if (name.startsWith("net.minecraft.server.v")) { // net.minecraft.server.v1_16_R1
                            String[] parts = name.split("\\.");
                            if (parts.length == 4) {
                                // correct package
                                plugin.getServer().getLogger().finest("Found package for NMS Reflection: " + name);

                                Class<?> REFLECT_CLASS = Class.forName(name + ".ItemStack");
                                plugin.getServer().getLogger().finest("[CLASS] ItemStack: " + REFLECT_CLASS);
                                REFLECT_COPYBUKKIT = REFLECT_CLASS.getMethod("fromBukkitCopy", ItemStack.class);
                                plugin.getServer().getLogger().finest("[METHOD] ItemStack.fromBukkitCopy: " + REFLECT_COPYBUKKIT);
                                REFLECT_GETTAG = REFLECT_CLASS.getMethod("getOrCreateTag");
                                plugin.getServer().getLogger().finest("[METHOD] ItemStack.getOrCreateTag: " + REFLECT_GETTAG);

                                Class<?> REFLECT_TAGCLASS = Class.forName(name + ".NBTTagCompound");
                                plugin.getServer().getLogger().finest("[CLASS] NBTTagCompound: " + REFLECT_TAGCLASS);
                                REFLECT_SETINT = REFLECT_TAGCLASS.getMethod("setInt", String.class, int.class);
                                plugin.getServer().getLogger().finest("[METHOD] NBTTagCompound.setInt: " + REFLECT_SETINT);
                                REFLECT_BUKKITMIRROR = REFLECT_CLASS.getMethod("asBukkitMirror");
                                plugin.getServer().getLogger().finest("[METHOD] NBTTagCompound.asBukkitMirror: " + REFLECT_BUKKITMIRROR);

                                plugin.getServer().getLogger().finest("All classes and methods found, everything should work!");

                                break;
                            }
                        }
                    }
                }
                Object nmsStack = REFLECT_COPYBUKKIT.invoke(null, input);
                Object tags = REFLECT_GETTAG.invoke(nmsStack);
                REFLECT_SETINT.invoke(tags, "RepairCost", 0);
                return (ItemStack)REFLECT_BUKKITMIRROR.invoke(nmsStack);
            } catch (Exception e) {
                REFLECT_ISSUE = true;
                plugin.getServer().getLogger().log(Level.SEVERE, "[AnvilEnchWear] Cannot reset RepairCost via Reflection, RepairCost will not reset until updated!");
                e.printStackTrace();
            }
        }
        return input; // failed
    }
}
