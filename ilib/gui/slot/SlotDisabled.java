package ilib.gui.slot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * @author Roj233
 * @since 2022/4/16 23:05
 */
public final class SlotDisabled extends Slot {
	public static final SlotDisabled DISABLED = new SlotDisabled();

	public SlotDisabled() {super(null, 0, 0, 0);}

	@Override
	public void putStack(ItemStack stack) {}

	@Override
	public ItemStack getStack() {
		return ItemStack.EMPTY;
	}

	public boolean isItemValid(ItemStack stack) {
		return false;
	}

	public boolean getHasStack() {
		return false;
	}

	public void onSlotChanged() {}

	public int getSlotStackLimit() {
		return 0;
	}

	public int getItemStackLimit(ItemStack stack) {
		return 0;
	}

	public ItemStack decrStackSize(int amount) {
		return ItemStack.EMPTY;
	}

	public boolean isHere(IInventory inv, int slotIn) {
		return false;
	}

	public boolean canTakeStack(EntityPlayer playerIn) {
		return false;
	}

	public boolean isEnabled() {
		return false;
	}

	public boolean isSameInventory(Slot other) {
		return false;
	}
}
