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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import static de.diddiz.LogBlock.config.Config.isLogging;
import static de.diddiz.util.BukkitUtils.*;

public class ChestAccessLogging extends LoggingListener {
    private final Map<HumanEntity, ItemStack[]> containers = new HashMap<>();
    private final Logger logger = LogBlock.getInstance().getLogger();

    public ChestAccessLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        final HumanEntity player = event.getPlayer();
        if (!isLogging(player.getWorld(), Logging.CHESTACCESS)) {
            return;
        }

        final Inventory inventory = event.getInventory();
        final InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof BlockState || holder instanceof DoubleChest)) {
            return;
        }

        final ItemStack[] before = containers.remove(player);
        if (before == null) {
            logger.log(Level.INFO, "Player closed inventory without first opening it, skipping.");
            return;
        }

        final ItemStack[] after = compressInventory(inventory.getContents());
        final ItemStack[] diff = compareInventories(before, after);

        final Location chestLocation = getInventoryHolderLocation(holder);
        if (chestLocation == null) {
            logger.log(Level.INFO, "Unknown chest location when closing inventory, skipping.");
            return;
        }

        for (final ItemStack stack : diff) {
            final ItemStack stackAbs = stack.clone();
            stackAbs.setAmount(Math.abs(stack.getAmount()));

            consumer.queueChestAccess(
                Actor.actorFromEntity(player),
                chestLocation,
                chestLocation.getWorld().getBlockAt(chestLocation).getBlockData(),
                stackAbs,
                stack.getAmount() < 0
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        final HumanEntity player = event.getPlayer();
        if (!isLogging(player.getWorld(), Logging.CHESTACCESS)) {
            return;
        }

        final Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        final InventoryHolder holder = event.getInventory().getHolder();
        if ((holder instanceof BlockState || holder instanceof DoubleChest) &&
                getInventoryHolderType(holder) != Material.CRAFTING_TABLE) {
            containers.put(player, compressInventory(inventory.getContents()));
        }
    }
}
