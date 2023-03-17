package ilib.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class ItemRightClickFirst extends ItemRightClick {
	@Override
	public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
		ItemStack stack = player.getHeldItem(hand);

		if (player.isSneaking()) return EnumActionResult.PASS;

		if (world.isBlockModifiable(player, pos)) {
			BlockPos targetPos = pos.offset(side);

			if (player.canPlayerEdit(pos, side, stack)) {
				return onRightClickFirst(world, targetPos, pos, player, stack) ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
			}
		}

		return EnumActionResult.PASS;
	}

	protected abstract boolean onRightClickFirst(World world, BlockPos targetPos, BlockPos clickPos, EntityPlayer player, ItemStack stack);
}
