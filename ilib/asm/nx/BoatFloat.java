package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.item.EntityBoat;

/**
 * @author Roj233
 * @since 2022/5/10 16:37
 */
@Nixim("net.minecraft.entity.item.EntityBoat")
class BoatFloat extends EntityBoat {
	public BoatFloat() {
		super(null);
	}

	@Inject("func_184444_v")
	private EntityBoat.Status getUnderwaterStatus() {
		return null;
	}
}
