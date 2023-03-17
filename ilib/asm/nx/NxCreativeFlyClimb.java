package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.player.EntityPlayer;

/**
 * @author solo6975
 * @since 2022/5/2 23:45
 */
@Nixim("net.minecraft.entity.player.EntityPlayer")
abstract class NxCreativeFlyClimb extends EntityPlayer {
	public NxCreativeFlyClimb() {
		super(null, null);
	}

	@Copy
	public boolean isOnLadder() {
		return !capabilities.isFlying && isOnLadder1();
	}

	@Shadow(value = "func_70617_f_", owner = "net.minecraft.entity.EntityLivingBase")
	private boolean isOnLadder1() {
		return false;
	}
}
