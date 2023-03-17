package ilib.gui.slot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * @author Roj234
 * @since 2021/1/13 13:03
 */
public class SlotFlagged extends Slot {
	public static final int UPDATE = 1, NO_TAKE = 2, NO_PUT = 4, NO_DISPLAY = 8;

	protected int flag;

	public SlotFlagged(IInventory inv, int index, int x, int y) {
		super(inv, index, x, y);
	}

	public SlotFlagged(IInventory inv, int index, int x, int y, int flag) {
		super(inv, index, x, y);
		this.flag = flag;
	}

	public void onSlotChanged() {
		if ((flag & UPDATE) != 0) inventory.markDirty();
	}

	@Override
	public boolean isEnabled() {
		return (flag & NO_DISPLAY) == 0;
	}

	@Override
	public boolean canTakeStack(EntityPlayer playerIn) {
		return (flag & NO_TAKE) == 0;
	}

	@Override
	public boolean isItemValid(ItemStack stack) {
		return (flag & NO_PUT) == 0;
	}

	public int getFlag() {
		return flag;
	}

	public void setFlag(int flag) {
		this.flag = flag;
	}
}
