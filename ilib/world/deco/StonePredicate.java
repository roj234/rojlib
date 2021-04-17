package ilib.world.deco;

import com.google.common.base.Predicate;

import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/**
 * @author Roj233
 * @since 2022/4/28 23:04
 */
public class StonePredicate implements Predicate<IBlockState> {
	public static final Predicate<IBlockState> INSTANCE = new StonePredicate();

	public StonePredicate() {}

	public boolean apply(IBlockState state) {
		if (state != null && state.getBlock() == Blocks.STONE) {
			return state.getValue(BlockStone.VARIANT).isNatural();
		} else {
			return false;
		}
	}
}
