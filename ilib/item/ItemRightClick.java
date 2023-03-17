package ilib.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class ItemRightClick extends ItemBase {
	@Override
	public final ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
		ItemStack stack = player.getHeldItem(hand);
		ItemStack result = onRightClick(world, player, stack, hand);
		return new ActionResult<>(result == null ? EnumActionResult.FAIL : EnumActionResult.SUCCESS, result == null ? stack : result);
	}

	protected ItemStack onRightClick(World world, EntityPlayer player, ItemStack stack, EnumHand hand) {
		RayTraceResult mop = this.rayTrace(world, player, false);

		if (mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK) {
			return null;
		}

		BlockPos clickPos = mop.getBlockPos();

		if (world.isBlockModifiable(player, clickPos)) {
			BlockPos targetPos = clickPos.offset(mop.sideHit);

			if (player.canPlayerEdit(clickPos, mop.sideHit, stack)) {
				return onRightClickBlock(world, targetPos, clickPos, player, stack, mop.sideHit);
			}
		}

		return null;
	}

	protected ItemStack onRightClickBlock(World world, BlockPos pos, BlockPos clickPos, EntityPlayer player, ItemStack stack, EnumFacing side) {
		throw new UnsupportedOperationException();
	}
}
