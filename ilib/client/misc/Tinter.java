package ilib.client.misc;

import ilib.api.BlockColor;
import ilib.api.ItemColor;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class Tinter implements IItemColor, IBlockColor {
	@Override
	public int colorMultiplier(ItemStack stack, int tintIndex) {
		return ((ItemColor) stack.getItem()).getColor(stack, tintIndex);
	}

	@Override
	public int colorMultiplier(IBlockState state, IBlockAccess world, BlockPos pos, int tintIndex) {
		return ((BlockColor) state.getBlock()).getColor(state, world, pos, tintIndex);
	}
}