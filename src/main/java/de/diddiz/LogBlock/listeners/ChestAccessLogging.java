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
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

import static de.diddiz.LogBlock.config.Config.isLogging;
import static de.diddiz.util.BukkitUtils.*;


public class ChestAccessLogging extends LoggingListener {
    private class ChestAccess {
        public List<HumanEntity> players;
        public ItemStack[] stack;

        public ChestAccess(ItemStack[] stack) {
            this.players = new ArrayList<HumanEntity>();
            this.stack = stack;
        }
    }

    private final Map<InventoryHolder, ChestAccess> openChests = new HashMap<>();
    private final Logger logger = LogBlock.getInstance().getLogger();

    public ChestAccessLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        logger.log(Level.INFO, "yeet");
        final Inventory initiator = event.getInitiator();
        if (!(initiator instanceof PlayerInventory)) {
            logger.log(Level.WARNING, "Initiator is not a player inventory");
            return;
        }

        final HumanEntity player = ((PlayerInventory) initiator).getHolder();
        if (!isLogging(player.getWorld(), Logging.CHESTACCESS)) {
            return;
        }


        logger.log(Level.INFO, "Player moved item");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        final InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockState || holder instanceof DoubleChest)) {
            return;
        }

        final ItemStack cursor = event.getCursor() != null ? event.getCursor().clone() : null;
        final ItemStack currentItem = event.getCurrentItem() != null ? event.getCurrentItem().clone() : null;

        final List<ItemStack> stacks = new ArrayList<>();
        switch (event.getAction()) {
        case PICKUP_ALL:
            stacks.add(currentItem);
            break;

        case PICKUP_HALF:
            currentItem.setAmount((int) Math.ceil(currentItem.getAmount() / 2.0));
            stacks.add(currentItem);
            break;

        case PICKUP_ONE:
            currentItem.setAmount(1);
            stacks.add(currentItem);
            break;

        case PLACE_ALL:
            cursor.setAmount(-cursor.getAmount());
            stacks.add(cursor);
            break;

        case PLACE_SOME:
            cursor.setAmount(cursor.getAmount() - cursor.getMaxStackSize());
            stacks.add(cursor);
            break;

        case PLACE_ONE:
            cursor.setAmount(-1);
            stacks.add(cursor);
            break;

        case SWAP_WITH_CURSOR:
            stacks.add(currentItem);
            cursor.setAmount(-1);
            stacks.add(cursor);
            break;

        case DROP_ALL_SLOT:
            stacks.add(currentItem);
            break;

        case DROP_ONE_SLOT:
            currentItem.setAmount(1);
            stacks.add(currentItem);
            break;

        case MOVE_TO_OTHER_INVENTORY:
            stacks.add(currentItem);
            break;

        case HOTBAR_MOVE_AND_READD:
            logger.log(Level.INFO, "hotbar move and readd");
            break;

        case HOTBAR_SWAP:
            logger.log(Level.INFO, "hotbar swap");
            break;

        default:
            logger.log(
                Level.INFO,
                event.getAction().toString() + "; cursor=" +
                (cursor != null ? cursor.toString() : "null") + "; " +
                (currentItem != null ? currentItem.toString() : null)
            );
        }

        logger.log(Level.INFO, stacks.toString());
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

        final ChestAccess chestAccess = openChests.get(holder);
        if (chestAccess == null) {
            logger.log(Level.INFO, "Player closed inventory without first opening it, skipping.");
            return;
        }

        final ItemStack[] after = compressInventory(inventory.getContents());
        final ItemStack[] diff = compareInventories(chestAccess.stack, after);

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

        final InventoryHolder holder = inventory.getHolder();
        if ((holder instanceof BlockState || holder instanceof DoubleChest) &&
                getInventoryHolderType(holder) != Material.CRAFTING_TABLE) {
            ChestAccess chestAccess = openChests.get(holder);
            if (chestAccess == null) {
                chestAccess = new ChestAccess(compressInventory(inventory.getContents()));
            }

            chestAccess.players.add(player);
            openChests.put(holder, chestAccess);
        }
    }
}
