package de.diddiz.LogBlock.listeners;

import de.diddiz.LogBlock.Actor;
import de.diddiz.LogBlock.LogBlock;
import de.diddiz.LogBlock.Logging;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

import static de.diddiz.LogBlock.config.Config.isLogging;
import static de.diddiz.util.BukkitUtils.*;

public class ChestAccessLogging extends LoggingListener {
    private final Map<HumanEntity, ItemStack[]> containers = new HashMap<>();

    public ChestAccessLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {

        if (!isLogging(event.getPlayer().getWorld(), Logging.CHESTACCESS)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockState || holder instanceof DoubleChest) {
            final HumanEntity player = event.getPlayer();
            final ItemStack[] before = containers.get(player);
            if (before == null) {
                return;
            }
            
            final ItemStack[] after = compressInventory(event.getInventory().getContents());
            final ItemStack[] diff = compareInventories(before, after);
            final Location location = getInventoryHolderLocation(holder);
            if (location != null) {
                for (final ItemStack stack : diff) {
                    ItemStack stackAbs = stack.clone();
                    stackAbs.setAmount(Math.abs(stack.getAmount()));
 
                    consumer.queueChestAccess(
                        Actor.actorFromEntity(player),
                        location,
                        location.getWorld().getBlockAt(location).getBlockData(),
                        stackAbs,
                        stack.getAmount() < 0
                    );
                }
            }
            
            containers.remove(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {

        if (!isLogging(event.getPlayer().getWorld(), Logging.CHESTACCESS)) {
            return;
        }
        if (event.getInventory() != null) {
            InventoryHolder holder = event.getInventory().getHolder();
            if (holder instanceof BlockState || holder instanceof DoubleChest) {
                if (getInventoryHolderType(holder) != Material.CRAFTING_TABLE) {
                    containers.put(event.getPlayer(), compressInventory(event.getInventory().getContents()));
                }
            }
        }
    }
}
