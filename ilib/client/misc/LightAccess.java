package ilib.client.misc;

import ilib.ClientProxy;
import ilib.util.BlockHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

import javax.annotation.Nullable;

/**
 * @author Roj233
 * @since 2022/4/23 23:59
 */
public class LightAccess implements IBlockAccess {
	public final IBlockState state;
	private final int light;

	public LightAccess(IBlockState state) {
		this.state = state;
		this.light = 15 << 20 | 15 << 4;
	}

	public LightAccess(IBlockState state, int skyLight, int blockLight) {
		this.state = state;
		this.light = combine(skyLight, blockLight);
	}

	public LightAccess(IBlockState state, int combinedLight) {
		this.state = state;
		this.light = combinedLight;
	}

	public static int combine(int sky, int block) {
		return (sky << 20) | (block << 4);
	}

	@Nullable
	@Override
	public TileEntity getTileEntity(BlockPos pos) {
		return null;
	}

	@Override
	public int getCombinedLight(BlockPos pos, int i) {
		return pos.equals(BlockPos.ORIGIN) ? light : light & ~255;
	}

	@Override
	public IBlockState getBlockState(BlockPos pos) {
		return pos.equals(BlockPos.ORIGIN) ? state : BlockHelper.AIR_STATE;
	}

	@Override
	public boolean isAirBlock(BlockPos pos) {
		return !pos.equals(BlockPos.ORIGIN);
	}

	@Override
	public Biome getBiome(BlockPos pos) {
		return ClientProxy.mc.world.getBiome(pos);
	}

	@Override
	public int getStrongPower(BlockPos pos, EnumFacing facing) {
		return 0;
	}

	@Override
	public WorldType getWorldType() {
		return ClientProxy.mc.world.getWorldType();
	}

	@Override
	public boolean isSideSolid(BlockPos pos, EnumFacing facing, boolean b) {
		return pos.equals(BlockPos.ORIGIN) && state.isSideSolid(this, pos, facing);
	}
}
