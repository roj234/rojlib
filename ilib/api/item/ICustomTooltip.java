package ilib.api.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * @author Roj234
 */
public interface ICustomTooltip {
	void onTooltip(List<String> list, ItemStack stack, EntityPlayer player);
}
