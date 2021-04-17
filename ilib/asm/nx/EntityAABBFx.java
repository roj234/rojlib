package ilib.asm.nx;

import ilib.misc.MCHooks;
import ilib.misc.MutAABB;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/6 17:17
 */
@Nixim("/")
abstract class EntityAABBFx extends Entity {
	public EntityAABBFx() {
		super(null);
	}

	@Copy(unique = true)
	static MutAABB getBox() {
		return MCHooks.box2.get();
	}

	@Inject("/")
	public boolean isOverWater() {
		return this.world.handleMaterialAcceleration(getBox().grow0(this.getEntityBoundingBox(), -0.001D, -20.001D, -0.001D), Material.WATER, this);
	}

	@Inject("/")
	public void fall(float distance, float damageMultiplier) {
		if (this.isBeingRidden()) {
			List<Entity> passengers = this.getPassengers();
			for (int i = 0; i < passengers.size(); i++) {
				passengers.get(i).fall(distance, damageMultiplier);
			}
		}
	}

	@Inject("/")
	public boolean isOffsetPositionInLiquid(double x, double y, double z) {
		return this.isLiquidPresentInAABB(getBox().offset0(getEntityBoundingBox(), x, y, z));
	}

	@Shadow("func_174809_b")
	private boolean isLiquidPresentInAABB(AxisAlignedBB bb) {
		return false;
	}

	@Inject("/")
	public boolean isInLava() {
		return this.world.isMaterialInBB(getBox().grow0(this.getEntityBoundingBox(), -0.1, -0.4, -0.1), Material.LAVA);
	}

	@Inject("/")
	public boolean handleWaterMovement() {
		if (this.getRidingEntity() instanceof EntityBoat) {
			this.inWater = false;
		} else if (this.world.handleMaterialAcceleration(getBox().grow0(getEntityBoundingBox(), -0.001D, -0.401D, -0.001D), Material.WATER, this)) {
			if (!this.inWater && !this.firstUpdate) {
				this.doWaterSplashEffect();
			}

			this.fallDistance = 0.0F;
			this.inWater = true;
			this.extinguish();
		} else {
			this.inWater = false;
		}

		return this.inWater;
	}
}
