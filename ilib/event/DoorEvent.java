package ilib.event;

import com.google.common.collect.ImmutableMap;
import ilib.util.BlockHelper;
import roj.collect.ToLongMap;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @author solo6975
 * @since 2022/4/5 20:59
 */
public class DoorEvent {
	private static final ToLongMap<BlockPos> time = new ToLongMap<>();

	@SubscribeEvent
	public static void onBlockUpdate(BlockEvent.NeighborNotifyEvent e) {
		World w = e.getWorld();
		if (w.isRemote) return;

		boolean open;
		IBlockState state = e.getState();

		ImmutableMap<IProperty<?>, Comparable<?>> props = state.getProperties();
		if (props.containsKey(BlockPressurePlate.POWERED)) {
			open = state.getValue(BlockPressurePlate.POWERED);
		} else if (props.containsKey(BlockPressurePlateWeighted.POWER) && !(state.getBlock() instanceof BlockRedstoneWire)) {
			open = state.getValue(BlockPressurePlateWeighted.POWER) > 0;
		} else if (props.containsKey(BlockButton.POWERED)) {
			open = state.getValue(BlockButton.POWERED);
		} else {
			return;
		}

		long t = time.getOrDefault(e.getPos(), 0);
		long t1 = System.currentTimeMillis();
		if (t1 - t < 25) return;
		time.putLong(e.getPos().toImmutable(), t1);

		openDoor(w, e.getPos(), !open, false);
	}

	@SubscribeEvent
	public static void onDoorClick(PlayerInteractEvent.RightClickBlock e) {
		World w = e.getWorld();
		if (w.isRemote) return;

		EntityPlayer player = e.getEntityPlayer();
		if (player.isSneaking()) return;

		if (w.getBlockState(e.getPos()).getMaterial() == Material.IRON) return;

		long t = time.getOrDefault(e.getPos(), 0);
		long t1 = System.currentTimeMillis();
		if (t1 - t < 25) return;
		time.putLong(e.getPos().toImmutable(), t);

		if (openDoor(w, e.getPos(), null, false)) {
			e.setUseBlock(Event.Result.DENY);
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onPlaceDoor(BlockEvent.EntityPlaceEvent e) {
		World w = e.getWorld();
		if (w.isRemote) return;

		Block block = e.getPlacedBlock().getBlock();
		if (block instanceof BlockDoor || block instanceof BlockTrapDoor || block instanceof BlockFenceGate) {
			time.putLong(e.getPos().toImmutable(), System.currentTimeMillis());
		}
	}

	public static boolean openDoor(World world, BlockPos pos, Boolean open, boolean inRecursion) {
		if (open == null) {
			open = !BlockDoor.isOpen(world, pos);
		}

		IBlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		if (block instanceof BlockDoor) {
			((BlockDoor) block).toggleDoor(world, pos, open);
		} else if (block instanceof BlockTrapDoor) {
			state = state.withProperty(BlockTrapDoor.OPEN, open);
		} else if (block instanceof BlockFenceGate) {
			state = state.withProperty(BlockFenceGate.OPEN, open);
		} else {
			return false;
		}

		if (!inRecursion) {
			boolean opened = false;
			int r = 2;
			for (BlockPos.MutableBlockPos pos1 : BlockPos.getAllInBoxMutable(pos.add(-r, -r, -r), pos.add(r, r, r))) {
				opened |= openDoor(world, pos1, open, true);
			}
			return opened;
		}

		if (!(block instanceof BlockDoor)) world.setBlockState(pos, state, BlockHelper.PLACEBLOCK_SENDCHANGE | BlockHelper.PLACEBLOCK_RENDERMAIN);

		int event;
		if (open) {
			event = state.getMaterial() == Material.IRON ? 1037 : 1007;
		} else {
			event = state.getMaterial() == Material.IRON ? 1036 : 1013;
		}
		world.playEvent(null, event, pos, 0);

		return true;
	}

}
