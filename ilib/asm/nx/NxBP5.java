package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2022/4/23 0:38
 */
@Nixim("/")
abstract class NxBP5 extends BlockRedstoneDiode {
	protected NxBP5() {
		super(false);
	}

	@Override
	@Inject("/")
	protected int getPowerOnSides(IBlockAccess worldIn, BlockPos pos, IBlockState state) {
		EnumFacing facing = state.getValue(FACING);
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
		EnumFacing side = facing.rotateY();
		int red = this.getPowerOnSide(worldIn, pos1.setPos(pos).move(side), side);
		if (red >= 15) {
			pos1.release();
			return red;
		}

		side = facing.rotateYCCW();
		red = Math.max(red, this.getPowerOnSide(worldIn, pos1.setPos(pos).move(side), side));
		pos1.release();
		return red;
	}

	@Override
	@Inject("/")
	protected int calculateInputStrength(World worldIn, BlockPos pos, IBlockState state) {
		EnumFacing side = state.getValue(FACING);
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
		int i = worldIn.getRedstonePower(pos1.setPos(pos).move(side), side);
		if (i >= 15) {
			pos1.release();
			return i;
		}

		state = worldIn.getBlockState(pos1);
		return Math.max(i, state.getBlock() == Blocks.REDSTONE_WIRE ? state.getValue(BlockRedstoneWire.POWER) : 0);
	}

	@Override
	@Inject("/")
	public boolean canBlockStay(World worldIn, BlockPos pos) {
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos).move(EnumFacing.DOWN);
		IBlockState state = worldIn.getBlockState(pos1);
		try {
			return state.isTopSolid() || state.getBlockFaceShape(worldIn, pos1, EnumFacing.UP) == BlockFaceShape.SOLID;
		} finally {
			pos1.release();
		}
	}
}
