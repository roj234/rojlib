package ilib.util.freeze;

import ilib.util.ForgeUtil;
import ilib.util.MCTexts;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 冻结的方块
 *
 * @author Roj233
 * @since 2021/8/26 19:36
 */
public final class FreezedBlock extends Block {
	static final PropertyInteger META;

	static {
		PropertyInteger m;
		if (ForgeUtil.findModById("moreid") != null) {
			try {
				m = PropertyInteger.create("meta", 0, (1 << Class.forName("moreid.Config").getDeclaredField("blockStateBits").getInt(null)) - 1);
			} catch (Throwable e) {
				m = PropertyInteger.create("meta", 0, 15);
			}
		} else {
			m = PropertyInteger.create("meta", 0, 15);
		}
		META = m;
	}

	public FreezedBlock() {
		super(Material.ROCK);
	}

	@Override
	@SuppressWarnings("deprecation")
	public float getBlockHardness(IBlockState blockState, World worldIn, BlockPos pos) {
		return -1;
	}

	@Override
	public float getExplosionResistance(World world, BlockPos pos, @Nullable Entity exploder, Explosion explosion) {
		return 999999;
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, META);
	}

	@Override
	public void harvestBlock(World worldIn, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack stack) {
		if (te != null && worldIn.getTileEntity(pos) == null) worldIn.addTileEntity(te);
		worldIn.setBlockState(pos, state);
	}

	@Override
	public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
		worldIn.setBlockState(pos, state);
	}

	@Override
	public String getTranslationKey() {
		return "tile.ilib.freezed";
	}

	@Override
	public String getLocalizedName() {
		return MCTexts.format(getTranslationKey()).trim();
	}

	@Override
	public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
		return new FreezedTileEntity(null);
	}

	@Override
	public boolean hasTileEntity(@Nonnull IBlockState state) {
		return true;
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(META, meta);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return state.getValue(META);
	}

	@Override
	public int hashCode() {
		return getRegistryName() == null ? 0 : getRegistryName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FreezedBlock && ((FreezedBlock) obj).getRegistryName().equals(getRegistryName());
	}
}
