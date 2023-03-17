package ilib.block;

import ilib.tile.TileEntityLootrChest;

import net.minecraft.block.BlockChest;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ILockableContainer;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2020/8/18 13:38
 */
public class BlockLootrChest extends BlockChest {
	public static IBlockState INSTANCE;

	public BlockLootrChest() {
		super(Type.BASIC);
		setSoundType(SoundType.WOOD);
		setResistance(Float.MAX_VALUE);
		setHardness(3f);
		INSTANCE = getDefaultState();
	}

	protected boolean isBelowSolidBlock(World worldIn, BlockPos pos) {
		return worldIn.getBlockState(pos.up()).doesSideBlockChestOpening(worldIn, pos.up(), EnumFacing.DOWN);
	}

	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if (!worldIn.isRemote) {
			TileEntity tile = worldIn.getTileEntity(pos);
			if (!(tile instanceof TileEntityLootrChest)) {
				return false;
			} else {
				TileEntityLootrChest chest = (TileEntityLootrChest) tile;
				if (this.isBelowSolidBlock(worldIn, pos)) {
					return false;
				} else {
					playerIn.displayGUIChest(chest.getInv(playerIn));
					//playerIn.addStat(StatList.CHEST_OPENED);
				}
			}
		}
		return true;
	}

	@Nullable
	public ILockableContainer getContainer(World worldIn, BlockPos pos, boolean allowBlocking) {
		return null;
	}

	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		return new TileEntityLootrChest();
	}

	public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
	}

	public boolean hasComparatorInputOverride(IBlockState state) {
		return false;
	}

	public int getComparatorInputOverride(IBlockState blockState, World worldIn, BlockPos pos) {
		return 0;
	}
}
