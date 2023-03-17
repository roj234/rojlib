package ilib.asm.nx.client;

import ilib.asm.util.MCHooksClient;
import ilib.misc.MutAABB;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * @author Roj233
 * @since 2022/4/23 0:38
 */
@Nixim("net.minecraft.client.renderer.entity.Render")
abstract class NxBP4 extends Render<Entity> {
	protected NxBP4() {
		super(null);
	}

	@Inject("/")
	public boolean shouldRender(Entity entity, ICamera c, double x, double y, double z) {
		AxisAlignedBB box = entity.getRenderBoundingBox();
		MutAABB box1 = MCHooksClient.box1;
		if (box.hasNaN() || box.getAverageEdgeLength() == 0.0D) {
			box1.set0(entity.posX - 2.0D, entity.posY - 2.0D, entity.posZ - 2.0D, entity.posX + 2.0D, entity.posY + 2.0D, entity.posZ + 2.0D);
		} else {
			box1.set0(box);
			box1.grow(0.5);
		}

		return (entity.ignoreFrustumCheck || c.isBoundingBoxInFrustum(box)) && entity.isInRangeToRender3d(x, y, z);
	}
}
