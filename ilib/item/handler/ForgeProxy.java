/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: InventoryMC.java
 */
package ilib.item.handler;

import net.minecraft.item.ItemStack;

import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;

public class ForgeProxy extends SimpleInventory {
	private final IItemHandlerModifiable inv;

	public ForgeProxy(IItemHandlerModifiable inv) {
		super("ILProxiedForgeInv");
		this.inv = inv;
	}

	@Override
	public int getSlotLimit(int id) {
		return id < 0 ? 64 : inv.getSlotLimit(id);
	}

	@Override
	public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
		return inv.isItemValid(slot, stack);
	}

	@Nonnull
	@Override
	public ItemStack extractItem(int id, int count, boolean simulate) {
		return inv.extractItem(id, count, simulate);
	}

	@Nonnull
	@Override
	public ItemStack insertItem(int id, @Nonnull ItemStack stack, boolean simulate) {
		return inv.insertItem(id, stack, simulate);
	}

	@Override
	public void setStackInSlot(int i, @Nonnull ItemStack stack) {
		inv.setStackInSlot(i, stack);
	}

	@Override
	public int getSlots() {
		return inv.getSlots();
	}

	@Nonnull
	@Override
	public ItemStack getStackInSlot(int i) {
		return inv.getStackInSlot(i);
	}
}
