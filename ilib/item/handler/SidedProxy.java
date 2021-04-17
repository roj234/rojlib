package ilib.item.handler;

import roj.collect.MyBitSet;

import net.minecraft.item.ItemStack;

import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;

/**
 * @author Roj233
 * @since 2022/4/15 10:42
 */
public class SidedProxy extends SimpleInventory {
	protected final IItemHandlerModifiable parent;
	protected final MyBitSet slots;

	public SidedProxy(IItemHandlerModifiable inventory, MyBitSet slots) {
		super("SidedProxy");
		this.parent = inventory;
		this.slots = slots;
	}

	@Override
	public int getSlots() {
		return slots.size();
	}

	@Override
	public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
		return parent.isItemValid(slots.nthTrue(slot), stack);
	}

	@Nonnull
	@Override
	public ItemStack getStackInSlot(int i) {
		return parent.getStackInSlot(slots.nthTrue(i));
	}

	@Override
	public void setStackInSlot(int i, @Nonnull ItemStack stack) {
		parent.setStackInSlot(slots.nthTrue(i), stack);
	}
}
