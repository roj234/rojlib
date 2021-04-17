package ilib.api.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public interface ILostThing {
	boolean isOwner(ItemStack stack, EntityPlayer player);

	boolean isOwned(ItemStack stack);
}
