package ilib.asm.nixim;

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
@Nixim("net.minecraft.client.renderer.ActiveRenderInfo")
abstract class NxBP4 extends Render<Entity> {
    protected NxBP4() {
        super(null);
    }

    @Inject("func_186703_a")
    public boolean shouldRender(Entity entity, ICamera c, double x, double y, double z) {
        AxisAlignedBB box = entity.getRenderBoundingBox();
        if (box.hasNaN() || box.getAverageEdgeLength() == 0.0D) {
            box = new AxisAlignedBB(entity.posX - 2.0D, entity.posY - 2.0D, entity.posZ - 2.0D, entity.posX + 2.0D, entity.posY + 2.0D, entity.posZ + 2.0D);
        }

        return (entity.ignoreFrustumCheck || c.isBoundingBoxInFrustum(box)) && entity.isInRangeToRender3d(x, y, z);
    }
}
