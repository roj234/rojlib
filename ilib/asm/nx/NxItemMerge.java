package ilib.asm.nx;

import ilib.misc.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.concurrent.OperationDone;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

/**
 * @author Roj233
 * @since 2022/5/7 2:19
 */
@Nixim("net.minecraft.entity.item.EntityItem")
abstract class NxItemMerge extends EntityItem {
	public NxItemMerge() {
		super(null);
	}

	@Inject("func_85054_d")
	private void searchForOtherItemsNearby() {
		ItemStack stack = getItem();
		if (stack.getCount() >= stack.getMaxStackSize()) return;
		try {
			this.world.getEntitiesWithinAABB(EntityItem.class, MCHooks.box1.grow0(this.getEntityBoundingBox(), 0.5D, 0.0D, 0.5D), item -> {
				this.combineItems(item);
				ItemStack stack1 = getItem();
				if (stack1.getCount() >= stack1.getMaxStackSize() || !isEntityAlive()) {
					throw OperationDone.INSTANCE;
				}
				return false;
			});
		} catch (OperationDone ignored) {}
	}

	@Shadow("func_70289_a")
	private boolean combineItems(EntityItem other) {
		return false;
	}
}
