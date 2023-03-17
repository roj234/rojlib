package ilib.item.handler;

import ilib.util.InventoryUtil;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;

/**
 * @author Roj233
 * @since 2022/4/13 22:19
 */
public abstract class SimpleInventory implements IItemHandler, IItemHandlerModifiable, IInventory, ICapabilityProvider {
	private final String name;

	public SimpleInventory(String name) {
		this.name = name;
	}

	@Override
	public final int getSizeInventory() {
		return getSlots();
	}

	@Override
	public ItemStack getStackInSlot1(int i) {
		return getStackInSlot(i);
	}

	@Override
	public final ItemStack decrStackSize(int id, int amount) {
		return extractItem(id, amount, false);
	}

	public ItemStack addStackSize(int id, int amount) {
		ItemStack stack = getStackInSlot(id);

		if (stack.isEmpty()) {
			throw new IllegalStateException("Try increase an empty ItemStack");
		}

		ItemStack added;

		if (amount == 0) return ItemStack.EMPTY;

		ItemStack backup = stack.copy();

		if (stack.getCount() + amount > stack.getMaxStackSize()) {
			added = stack.copy();
			added.setCount(stack.getCount() + amount - stack.getMaxStackSize());

			stack.setCount(stack.getMaxStackSize());
		} else {
			added = ItemStack.EMPTY;

			stack.setCount(stack.getCount() + amount);
		}

		inventoryChanged(id, backup, stack);

		return added;
	}

	@Override
	public final ItemStack removeStackFromSlot(int id) {
		return extractItem(id, getStackInSlot(id).getCount(), false);
	}

	@Override
	public final void setInventorySlotContents(int i, ItemStack stack) {
		setStackInSlot(i, stack);
	}

	@Override
	public int getInventoryStackLimit() {
		return getSlotLimit(-1);
	}

	@Override
	public void markDirty() {}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return true;
	}

	@Override
	public void openInventory(EntityPlayer player) {}

	@Override
	public void closeInventory(EntityPlayer player) {}

	@Override
	public final boolean isItemValidForSlot(int i, ItemStack stack) {
		return isItemValid(i, stack);
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int val) {}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean hasCustomName() {
		return false;
	}

	@Override
	public ITextComponent getDisplayName() {
		return new TextComponentTranslation(name);
	}

	public boolean isEmpty() {
		for (int i = 0; i < getSlots(); i++) {
			if (!getStackInSlot(i).isEmpty()) return false;
		}
		return true;
	}

	public void clear() {
		for (int i = 0; i < getSlots(); i++) {
			setStackInSlot(i, ItemStack.EMPTY);
		}
	}

	@Nonnull
	@Override
	public ItemStack insertItem(int id, @Nonnull ItemStack stack, boolean simulate) {
		if (!isItemValid(id, stack)) return stack;

		if (stack.isEmpty()) return ItemStack.EMPTY;

		ItemStack prev = getStackInSlot(id);
		if (prev.isEmpty()) {
			if (stack.getCount() > getSlotLimit(id)) {
				ItemStack copy = stack.copy();
				ItemStack target = copy.splitStack(getSlotLimit(id));
				if (!simulate) {
					setStackInSlot(id, target);
				}

				return copy;
			}
			if (!simulate) {
				setStackInSlot(id, stack);
			}

			return ItemStack.EMPTY;
		} else if (InventoryUtil.areItemStacksEqual(prev, stack)) {
			int sum = stack.getCount() + prev.getCount();

			int max = Math.min(getSlotLimit(id), stack.getMaxStackSize());

			if (sum > max) {
				ItemStack target = stack.copy();
				target.setCount(sum - max);
				if (!simulate) {
					ItemStack newStack = prev.copy();
					newStack.setCount(max);
					setStackInSlot(id, newStack);
				}
				return target;
			} else {
				if (!simulate) {
					ItemStack newStack = prev.copy();
					newStack.setCount(sum);
					setStackInSlot(id, newStack);
				}
				return ItemStack.EMPTY;
			}
		}

		return stack;
	}

	@Nonnull
	public ItemStack extractItem(int id, int count, boolean simulate) {
		if (count == 0) return ItemStack.EMPTY;

		ItemStack stack = getStackInSlot(id);
		if (stack.isEmpty()) return ItemStack.EMPTY;

		if (!canExtract(id, stack)) return ItemStack.EMPTY;

		ItemStack removed;

		if (stack.getCount() <= count) {
			removed = stack.copy();
			if (!simulate) {
				setStackInSlot(id, ItemStack.EMPTY);
			}
		} else {
			removed = stack.copy();
			removed.setCount(count);
			if (!simulate) {
				if (stack.getCount() <= count) {
					setStackInSlot(id, ItemStack.EMPTY);
				} else {
					ItemStack newStack = stack.copy();
					newStack.shrink(count);
					setStackInSlot(id, newStack);
				}
			}
		}


		return removed;
	}

	protected boolean canExtract(int id, ItemStack stack) {
		return true;
	}

	@Override
	public int getSlotLimit(int id) {
		return 64;
	}

	public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
		return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
	}

	@SuppressWarnings("unchecked")
	public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {return (T) this;} else return null;
	}

	/**
	 * getStackInSlot(slot) = to
	 */
	protected void inventoryChanged(int slot, ItemStack from, ItemStack to) {

	}
}
