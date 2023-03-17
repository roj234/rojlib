package ilib.api.item;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Deprecated
public interface ITooltip {
	void addTooltip(ItemStack is, List<String> list);
}
