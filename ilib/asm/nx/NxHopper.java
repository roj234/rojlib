package ilib.asm.nx;

import ilib.misc.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.IHopper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import net.minecraftforge.items.VanillaInventoryCodeHooks;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/6 18:15
 */
@Nixim("net.minecraft.tileentity.TileEntityHopper")
abstract class NxHopper extends TileEntityHopper {
	@Shadow("field_145900_a")
	private NonNullList<ItemStack> inventory;

	@Shadow("func_174917_b")
	private static boolean isInventoryEmpty(IInventory inv, EnumFacing side) {
		return false;
	}

	@Shadow("func_174921_b")
	private static boolean canExtractItemFromSlot(IInventory inv, ItemStack stack, int index, EnumFacing side) {
		return false;
	}

	@Shadow("func_174915_a")
	private static boolean pullItemFromSlot(IHopper hopper, IInventory inv, int index, EnumFacing dir) {
		return false;
	}

	@Inject("func_152104_k")
	private boolean isInventoryEmpty() {
		NonNullList<ItemStack> inv = this.inventory;
		for (int i = 0; i < inv.size(); i++) {
			if (!inv.get(i).isEmpty()) return false;
		}
		return true;
	}

	@Inject("func_152105_l")
	private boolean isFull() {
		NonNullList<ItemStack> inv = this.inventory;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.get(i);
			if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return false;
		}
		return true;
	}

	@Inject("func_145891_a")
	public static boolean pullItems(IHopper H) {
		Boolean ret = VanillaInventoryCodeHooks.extractHook(H);
		if (ret != null) {
			return ret;
		} else {
			IInventory src = getSourceInventory(H);
			if (src != null) {
				EnumFacing dir = EnumFacing.DOWN;
				if (isInventoryEmpty(src, dir)) {
					return false;
				}

				if (src instanceof ISidedInventory) {
					ISidedInventory inv1 = (ISidedInventory) src;
					for (int i : inv1.getSlotsForFace(dir)) {
						if (pullItemFromSlot(H, src, i, dir)) {
							return true;
						}
					}
				} else {
					int j = src.getSizeInventory();

					for (int k = 0; k < j; ++k) {
						if (pullItemFromSlot(H, src, k, dir)) {
							return true;
						}
					}
				}
			} else {
				BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(MathHelper.floor(H.getXPos()), MathHelper.floor(H.getYPos()) + 1, MathHelper.floor(H.getZPos()));
				IBlockState state = H.getWorld().getBlockState(pos);
				if (Block.FULL_BLOCK_AABB.equals(state.getCollisionBoundingBox(H.getWorld(), pos))) {
					pos.release();
					return false;
				}
				pos.release();

				List<EntityItem> items = getCaptureItems(H.getWorld(), H.getXPos(), H.getYPos(), H.getZPos());
				for (int i = 0; i < items.size(); i++) {
					EntityItem item = items.get(i);
					if (putDropInInventoryAllSlots(null, H, item)) {
						return true;
					}
				}
			}

			return false;
		}
	}

	@Inject("func_145898_a")
	public static boolean putDropInInventoryAllSlots(IInventory fr, IInventory to, EntityItem entity) {
		if (entity != null) {
			ItemStack remain = putStackInInventoryAllSlots(fr, to, entity.getItem(), null);
			if (remain.isEmpty()) {
				entity.setItem(ItemStack.EMPTY);
				entity.setDead();
				return true;
			} else {
				entity.setItem(remain);
			}
		}
		return false;
	}

	@Inject("func_184292_a")
	public static List<EntityItem> getCaptureItems(World w, double x, double y, double z) {
		MCHooks.box1.set0(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 1.5D, z + 0.5D);
		return w.getEntitiesWithinAABB(EntityItem.class, MCHooks.box1, EntitySelectors.IS_ALIVE);
	}

	@Inject("func_145893_b")
	public static IInventory getInventoryAtPosition(World w, double x, double y, double z) {
		IInventory inv = null;
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
		TileEntity tile = w.getTileEntity(pos);
		IBlockState state = w.getBlockState(pos);
		if (tile instanceof IInventory) {
			inv = (IInventory) tile;
			Block block = state.getBlock();
			if (inv instanceof TileEntityChest && block instanceof BlockChest) {
				inv = ((BlockChest) block).getContainer(w, pos, true);
			}
		} else if (!state.getBlock().isAir(state, w, pos)) {
			MCHooks.box1.set0(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D);
			List<Entity> list = w.getEntitiesWithinAABB(Entity.class, MCHooks.box1, EntitySelectors.HAS_INVENTORY);
			if (!list.isEmpty()) {
				inv = (IInventory) list.get(w.rand.nextInt(list.size()));
			}
		}

		pos.release();

		return inv;
	}
}
