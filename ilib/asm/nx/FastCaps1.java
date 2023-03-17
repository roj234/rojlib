package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;

/**
 * @author Roj234
 * @since 2023/1/6 0006 21:26
 */
@Nixim("net.minecraft.item.ItemStack")
class FastCaps1 {
	@Shadow(value = "/")
	private CapabilityDispatcher capabilities;

	@Inject(value = "/", at = Inject.At.TAIL)
	private void forgeInit() {
		if (capabilities != null) ((FastCaps2)(Object)capabilities).setTrackable();
	}
}
