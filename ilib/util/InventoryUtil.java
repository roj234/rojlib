/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: Paul Davis(Bookshelf - Java)
 * Filename: InventoryUtils.java
 */
package ilib.util;

import roj.collect.IntIterator;
import roj.collect.MyBitSet;
import roj.math.MathUtils;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.PrimitiveIterator;

public class InventoryUtil {
	/**
	 * 从给定的Inventory计算比较器输出
	 */
	public static int calcRedstoneFromInventory(IInventory inv) {
		if (inv == null) return 0;

		float f = 0;
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack stack = inv.getStackInSlot1(i);
			if (!stack.isEmpty()) {
				f += (float) stack.getCount() / (float) Math.min(stack.getMaxStackSize(), inv.getInventoryStackLimit());
			}
		}
		return (int) MathUtils.interpolate(f / inv.getSizeInventory(), 0, 1, 0, 15);
	}

	/**
	 * 从给定的Inventory计算比较器输出
	 */
	public static int calcRedstoneFromInventory(IItemHandler inv) {
		if (inv == null) return 0;

		float f = 0;
		for (int i = 0; i < inv.getSlots(); i++) {
			ItemStack stack = inv.getStackInSlot(i);
			if (!stack.isEmpty()) {
				f += (float) stack.getCount() / (float) Math.min(stack.getMaxStackSize(), inv.getSlotLimit(i));
			}
		}
		return (int) MathUtils.interpolate(f / inv.getSlots(), 0, 1, 0, 15);
	}

	/**
	 * 能否合并
	 */
	public static boolean canStacksMerge(ItemStack stackToMerge, ItemStack stackInSlot) {
		return stackInSlot.isEmpty() || areItemStacksEqual(stackToMerge, stackInSlot);
	}

	public static boolean tryMergeStacks(ItemStack stackToMerge, ItemStack stackInSlot) {
		if (stackInSlot.isEmpty() || !stackInSlot.isItemEqual(stackToMerge) || !ItemStack.areItemStackTagsEqual(stackToMerge, stackInSlot)) return false;

		int newStackSize = stackInSlot.getCount() + stackToMerge.getCount();
		int maxStackSize = stackToMerge.getMaxStackSize();

		if (newStackSize <= maxStackSize) {
			stackToMerge.setCount(0);
			stackInSlot.setCount(newStackSize);
			return true;
		} else if (stackInSlot.getCount() < maxStackSize) {
			stackToMerge.setCount(stackToMerge.getCount() - maxStackSize - stackInSlot.getCount());
			stackInSlot.setCount(maxStackSize);
			return true;
		} else {return false;}
	}

	/**
	 * ItemStack相等
	 * 又一个轮子
	 */
	public static boolean areItemStacksEqual(ItemStack s1, ItemStack s2) {
		return s1.getItem() == s2.getItem() && (s1.getItemDamage() == s2.getItemDamage() || s1.getItemDamage() == 32767 || s2.getItemDamage() == 32767) && (s1.hasTagCompound() == s2.hasTagCompound() && (!s1.hasTagCompound() || s1.getTagCompound()
																																																									 .equals(
																																																										 s2.getTagCompound()))) && (s1.areCapsCompatible(
			s2));
	}

	public static boolean moveItemInto(@Nullable Object source, int fromSlot, @Nullable Object target, int intoSlot, int max, EnumFacing dir, boolean doMove) {
		// Object to hold source
		IItemHandler from;

		// If source is not an item handler, attempt to cast
		if (!(source instanceof IItemHandler)) {
			if (source instanceof IInventory) {
				if (!(source instanceof ISidedInventory) || dir == null) {
					from = new InvWrapper((IInventory) source); // Wrap vanilla inventory
				} else {
					from = new SidedInvWrapper((ISidedInventory) source, dir.getOpposite()); // Wrap sided
				}
			} else if (source instanceof TileEntity) {
				TileEntity tile = (TileEntity) source;
				// use TE's itemHandler
				from = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir);
				if (from == null) return false;
			} else {return false;}
		} else {
			// Cast item handlers
			from = (IItemHandler) source;
		}

		IItemHandler to;

		// If sink is not an item handler, attempt to cast
		if (!(target instanceof IItemHandler)) {
			if (target instanceof IInventory) {
				if (!(target instanceof ISidedInventory) || dir == null) {
					to = new InvWrapper((IInventory) target); // Wrap vanilla inventory
				} else {
					to = new SidedInvWrapper((ISidedInventory) target, dir); // Wrap sided
				}
			} else if (target instanceof TileEntity) {
				TileEntity tile = (TileEntity) target;
				// use TE's itemHandler
				to = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir == null ? null : dir.getOpposite());
				if (to == null) return false;
			} else {return false;}
		} else {
			to = (IItemHandler) target;
		}

		MyBitSet fromSlots = new MyBitSet();
		MyBitSet toSlots = new MyBitSet();

		// Add From Slots
		if (fromSlot >= 0) {fromSlots.add(fromSlot);} else fromSlots.fill(from.getSlots());

		// Add to slots
		if (intoSlot >= 0) {toSlots.add(intoSlot);} else toSlots.fill(to.getSlots());

		// Do actual movement
		boolean hasAnyMove = false;
		IntIterator tItr = toSlots.iterator();
		for (PrimitiveIterator.OfInt fItr = fromSlots.iterator(); fItr.hasNext(); ) {
			if (toSlots.size() == 0) break;
			fromSlot = fItr.nextInt();

			ItemStack fromStack = from.extractItem(fromSlot, max, true); // Simulate to get stack
			if (!fromStack.isEmpty()) { // Make sure we got something
				tItr.reset();
				while (tItr.hasNext()) {
					int toSlot = tItr.nextInt();

					ItemStack remain = to.insertItem(toSlot, fromStack, !doMove);
					if (!ItemStack.areItemStacksEqual(fromStack, remain)) { // If a change was made to the stack
						if (!doMove) return true;

						int count = Math.min(fromStack.getCount() - remain.getCount(), max);
						// Extract from original
						from.extractItem(fromSlot, count, false);
						hasAnyMove = true;

						max -= count;
						if (max <= 0) return true;

						remain = to.getStackInSlot(toSlot);
						if (remain.getCount() >= Math.min(to.getSlotLimit(toSlot), remain.getMaxStackSize())) {
							tItr.remove();
						}
					}
					fromStack = remain;
				}
			}
		}
		return hasAnyMove;
	}

	/**
	 * Find whether player had an item
	 *
	 * @param player who
	 * @param item item
	 *
	 * @return found stack
	 */
	@Nullable
	//@Deprecated
	public static ItemStack isItemInHotbar(EntityPlayer player, Item item) {
		return isItemInHotbar(player, item, -1);
	}

	/**
	 * Find whether player had an item with specified damage
	 *
	 * @param player who
	 * @param item item
	 * @param damage damage value
	 *
	 * @return found stack
	 */
	@Nullable
	public static ItemStack isItemInHotbar(@Nonnull EntityPlayer player, @Nonnull Item item, int damage) {
		final int FIRST_HOTBAR_SLOT = 0;
		final int LAST_HOTBAR_SLOT_PLUS_ONE = FIRST_HOTBAR_SLOT + InventoryPlayer.getHotbarSize();

		for (int i = FIRST_HOTBAR_SLOT; i < LAST_HOTBAR_SLOT_PLUS_ONE; i++) {
			ItemStack stack = player.inventory.getStackInSlot1(i);
			if (stack.getItem() == item && (damage == -1 || stack.getItemDamage() == damage)) {
				return stack;
			}
		}
		return null;
	}

	/**
	 * Find whether player had an item within an item class
	 * Slow
	 *
	 * @param player who
	 * @param clazz item class
	 *
	 * @return found stack
	 */
	@Nullable
	@Deprecated
	public static ItemStack isItemInHotbar(@Nonnull EntityPlayer player, @Nonnull Class<?> clazz) {
		final int FIRST_HOTBAR_SLOT = 0;
		final int LAST_HOTBAR_SLOT_PLUS_ONE = FIRST_HOTBAR_SLOT + InventoryPlayer.getHotbarSize();

		for (int i = FIRST_HOTBAR_SLOT; i < LAST_HOTBAR_SLOT_PLUS_ONE; i++) {
			ItemStack stack = player.inventory.getStackInSlot1(i);
			if (clazz.isInstance(stack.getItem())) {
				return stack;
			}
		}
		return null;
	}

	/**
	 * Find whether player had an item with specified capability
	 *
	 * @param player who
	 * @param cap the capability
	 *
	 * @return the capability instance
	 */
	@Nullable
	public static <T> T isItemInHotbar(@Nonnull EntityPlayer player, @Nonnull Capability<T> cap) {
		final int FIRST_HOTBAR_SLOT = 0;
		final int LAST_HOTBAR_SLOT_PLUS_ONE = FIRST_HOTBAR_SLOT + InventoryPlayer.getHotbarSize();

		T capr;
		for (int i = FIRST_HOTBAR_SLOT; i < LAST_HOTBAR_SLOT_PLUS_ONE; i++) {
			ItemStack stack = player.inventory.getStackInSlot1(i);
			if (!stack.isEmpty() && (capr = stack.getCapability(cap, null)) != null) {
				return capr;
			}
		}
		return null;
	}

	/**
	 * Find whether player had an item with specified capability
	 *
	 * @param player who
	 * @param cap the capability
	 * @param list list to add these capabilities
	 *
	 * @return the list
	 */
	@Nonnull
	public static <T> List<T> isItemInHotbar(@Nonnull EntityPlayer player, @Nonnull Capability<T> cap, @Nonnull List<T> list) {
		final int FIRST_HOTBAR_SLOT = 0;
		final int LAST_HOTBAR_SLOT_PLUS_ONE = FIRST_HOTBAR_SLOT + InventoryPlayer.getHotbarSize();

		T capr;
		for (int i = FIRST_HOTBAR_SLOT; i < LAST_HOTBAR_SLOT_PLUS_ONE; i++) {
			ItemStack stack = player.inventory.getStackInSlot1(i);
			if (!stack.isEmpty() && (capr = stack.getCapability(cap, null)) != null) {
				list.add(capr);
			}
		}
		return list;
	}

	/**
	 * Replace player's item
	 *
	 * @param player who
	 * @param item the item
	 * @param stack new item
	 *
	 * @return replaced
	 */
	@Deprecated
	public static boolean replaceItemInHotbar(@Nonnull EntityPlayer player, @Nonnull Item item, ItemStack stack) {
		return replaceItemInHotbar(player, item, -1, stack);
	}

	/**
	 * Replace player's item
	 *
	 * @param player who
	 * @param oldStack old item
	 * @param newStack new item
	 *
	 * @return replaced
	 */
	public static boolean replaceItemInHotbar(@Nonnull EntityPlayer player, @Nonnull ItemStack oldStack, @Nonnull ItemStack newStack) {
		final int FIRST_HOTBAR_SLOT = 0;
		final int LAST_HOTBAR_SLOT_PLUS_ONE = FIRST_HOTBAR_SLOT + InventoryPlayer.getHotbarSize();

		for (int i = FIRST_HOTBAR_SLOT; i < LAST_HOTBAR_SLOT_PLUS_ONE; i++) {
			ItemStack stack = player.inventory.getStackInSlot1(i);
			if (stack == oldStack) {
				player.inventory.setInventorySlotContents(i, newStack);
				return true;
			}
		}
		return false;
	}

	/**
	 * Replace player's item
	 *
	 * @param player who
	 * @param item the item
	 * @param damage the item's damage
	 * @param newStack new item
	 *
	 * @return replaced
	 */
	public static boolean replaceItemInHotbar(@Nonnull EntityPlayer player, @Nonnull Item item, int damage, @Nonnull ItemStack newStack) {
		final int FIRST_HOTBAR_SLOT = 0;
		final int LAST_HOTBAR_SLOT_PLUS_ONE = FIRST_HOTBAR_SLOT + InventoryPlayer.getHotbarSize();

		for (int i = FIRST_HOTBAR_SLOT; i < LAST_HOTBAR_SLOT_PLUS_ONE; i++) {
			ItemStack stack = player.inventory.getStackInSlot1(i);
			if (stack.getItem() == item && (damage == -1 || stack.getItemDamage() == damage)) {
				player.inventory.setInventorySlotContents(i, newStack);
				return true;
			}
		}
		return false;
	}
}
