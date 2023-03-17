package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumHand;

/**
 * @author Roj233
 * @since 2022/5/27 14:10
 */
@Nixim("/")
class FixFurnaceCart extends EntityMinecartFurnace {
	@Shadow
	private int fuel;

	FixFurnaceCart() {
		super(null);
	}

	@Inject("/")
	public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
		if (!super.processInitialInteract(player, hand)) {
			ItemStack stack = player.getHeldItem(hand);
			int fuel1 = TileEntityFurnace.getItemBurnTime(stack);
			if (fuel1 > 0 && fuel + fuel1 <= 32767) {
				if (!player.capabilities.isCreativeMode) {
					stack.shrink(1);
				}

				fuel += fuel1;
			}

			pushX = posX - player.posX;
			pushZ = posZ - player.posZ;
		}
		return true;
	}
}
