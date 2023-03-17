package ilib.asm.nx.client;

import ilib.util.BlockHelper;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * @author Roj234
 * @since 2020/8/20 22:42
 */
@Nixim("/")
abstract class NxFastSpawn extends Entity {
	public NxFastSpawn(World worldIn) {
		super(worldIn);
	}

	@Inject("/")
	protected void preparePlayerToSpawn() {
		if (this.world != null) {
			if (this.posY > 0 && this.posY < 256) {
				this.posY = BlockHelper.getSurfaceBlockY(this.world, (int) Math.round(this.posX), (int) Math.round(this.posZ));
				this.setPosition(this.posX, this.posY, this.posZ);
			}
			this.motionX = 0;
			this.motionY = 0;
			this.motionZ = 0;
			this.rotationPitch = 0;
		}
	}

}
