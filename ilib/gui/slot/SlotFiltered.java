package ilib.gui.slot;

import ilib.api.IItemFilter;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/1/13 13:03
 */
public class SlotFiltered extends SlotFlagged {
	private final IItemFilter valid;
	private final int id;

	/**
	 * Customized slot: auto check can input / output
	 *
	 * @param inv The tile entity
	 * @param index The slot index
	 * @param x The x offset
	 * @param y The y offset
	 */
	public <E extends IInventory & IItemFilter> SlotFiltered(E inv, int index, int x, int y, int id) {
		super(inv, index, x, y);
		this.valid = inv;
		this.id = id;
	}

	/**
	 * Customized slot: auto check can input / output
	 *
	 * @param inv The tile entity
	 * @param index The slot index
	 * @param valid The stack verifier
	 * @param x The x offset
	 * @param y The y offset
	 */
	public SlotFiltered(IInventory inv, int index, int x, int y, IItemFilter valid, int id) {
		super(inv, index, x, y);
		this.valid = valid;
		this.id = id;
	}

	@Override
	public boolean isItemValid(@Nonnull ItemStack stack) {
		return valid.isItemValid(id, stack);
	}
}
