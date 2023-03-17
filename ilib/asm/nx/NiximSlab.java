package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;

/**
 * @author Roj234
 * @since 2020/11/22 15:50
 */
@Nixim("net.minecraft.block.BlockSlab")
abstract class NiximSlab extends BlockSlab {
	public NiximSlab(Material materialIn) {
		super(materialIn);
	}

	@Copy(unique = true, staticInitializer = "init0", targetIsFinal = true)
	private static Random MY_RANDOM;

	private static void init0() {
		MY_RANDOM = new Random();
	}

	@Copy(unique = true)
	public static boolean breaking;

	@Override
	@Copy
	public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
		this.onBlockHarvested(world, pos, state, player);

		IBlockState state1 = Blocks.AIR.getDefaultState();
		check:
		if (isDouble() && !player.isSneaking() && quantityDropped(MY_RANDOM) == 999) {
			RayTraceResult result = player.rayTrace(player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue(), 0);
			if (result != null) {
				BlockSlab slab;
				try {
					slab = (BlockSlab) ((ItemBlock) getItemDropped(state, world.rand, 0)).getBlock();
				} catch (Throwable e) {
					break check;
				}

				int side = getSide(player, result) ? 0 : 8;
				int meta = getMetaFromState(state) & 7;

				breaking = true;
				state1 = slab.getStateFromMeta(meta | side);
			}
		}

		return world.setBlockState(pos, state1, world.isRemote ? 11 : 3);
	}

	@Copy
	private static boolean getSide(EntityPlayer player, RayTraceResult result) {
		Vec3d hit = result.hitVec;

		double hitY = hit.y;
		double relativeY = hitY - (int) hitY;

		if (relativeY == 0) {
			return !(hitY >= player.posY + player.getEyeHeight());
		}

		// hit at y>0.5
		else {return relativeY >= 0.5;}
	}

	@Inject("/")
	public int quantityDropped(Random random) {
		if (random == MY_RANDOM) return 999;
		if (breaking) {
			breaking = false;
			return 1;
		}
		return this.isDouble() ? 2 : 1;
	}
}
