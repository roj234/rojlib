package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/2/16 13:23
 */
@Nixim("/")
abstract class CrashDispenser extends World {
	protected CrashDispenser(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client) {
		super(saveHandlerIn, info, providerIn, profilerIn, client);
	}

	@Inject("/")
	public boolean mayPlace(Block blockIn, BlockPos pos, boolean skipCollisionCheck, EnumFacing sidePlacedOn, @Nullable Entity placer) {
		if (!this.isValid(pos) || this.isOutsideBuildHeight(pos)) return false;

		IBlockState state = this.getBlockState(pos);
		AxisAlignedBB box = skipCollisionCheck ? null : blockIn.getDefaultState().getCollisionBoundingBox(this, pos);
		if (!(placer instanceof EntityPlayer) && ForgeEventFactory.onBlockPlace(placer, new BlockSnapshot(this, pos, blockIn.getDefaultState()), sidePlacedOn).isCanceled()) {
			return false;
		} else if (box != null && box != Block.NULL_AABB && !this.checkNoEntityCollision(box.offset(pos))) {
			return false;
		} else if (state.getMaterial() == Material.CIRCUITS && blockIn == Blocks.ANVIL) {
			return true;
		} else {
			return state.getBlock().isReplaceable(this, pos) && blockIn.canPlaceBlockOnSide(this, pos, sidePlacedOn);
		}
	}
}
