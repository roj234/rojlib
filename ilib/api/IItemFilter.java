package ilib.api;

import net.minecraft.item.ItemStack;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public interface IItemFilter {
	boolean isItemValid(int id, ItemStack stack);
}
