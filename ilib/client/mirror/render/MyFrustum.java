package ilib.client.mirror.render;

import ilib.ClientProxy;
import ilib.util.EntityHelper;
import roj.math.MathUtils;
import roj.math.Vec3d;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * @author Roj233
 * @since 2022/4/25 12:27
 */
public class MyFrustum implements ICamera {
	private final Vec3d dir, pos, ray;
	private final float maxDist;

	public MyFrustum(AxisAlignedBB p, Entity entity) {
		dir = EntityHelper.direction(entity);
		pos = new Vec3d();
		pos.y = entity.getEyeHeight();
		ray = new Vec3d();

		float d = ClientProxy.mc.gameSettings.renderDistanceChunks << 4;
		maxDist = d * d;
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB bb) {
		Vec3d pos = this.pos;
		Vec3d dir = this.dir;
		Vec3d ray = this.ray;

		double sx = MathUtils.clamp(pos.x, bb.minX, bb.maxX), sy = MathUtils.clamp(pos.y, bb.minY, bb.maxY), sz = MathUtils.clamp(pos.z, bb.minZ, bb.maxZ);

		for (int i = 0; i < 7; i++) {
			double x = sx;
			double y = sy;
			double z = sz;

			switch (i) {
				case 0:
					x = bb.maxX;
					break;
				case 1:
					x = bb.minX;
					break;
				case 2:
					y = bb.maxY;
					break;
				case 3:
					y = bb.minY;
					break;
				case 4:
					z = bb.maxZ;
					break;
				case 5:
					z = bb.minZ;
					break;
				case 6:
					x = dir.x < 0 ? bb.minX : bb.maxX;
					y = dir.y < 0 ? bb.minY : bb.maxY;
					z = dir.z < 0 ? bb.minZ : bb.maxZ;
					break;
			}

			// 原点射向目标的直线
			ray.set(x - pos.x, y - pos.y, z - pos.z);

			if (ray.len2() > maxDist) continue;

			double angle = Math.acos(ray.angle(dir));
			double fov_angle = Math.toRadians(70) * Math.pow(ray.len(), -0.08);

			if (angle <= fov_angle) {
				// todo 区块与玩家夹角 <= 传送门与玩家夹角
                /*AxisAlignedBB plane = portalBB;

                double x1 = bb.minX < plane.minX ? bb.minX : bb.maxX;
                double y1 = bb.minY < plane.minY ? bb.minY : bb.maxY;
                double z1 = bb.minZ < plane.minZ ? bb.minZ : bb.maxZ;

                ray.set(x1, y1, z1);

                if (Math.acos(ray.angle(dir)) < fov[0]) */
				return true;
			}
		}
		return false;
	}

	@Override
	public void setPosition(double x, double y, double z) {
		pos.x = x;
		pos.y += y;
		pos.z = z;
	}
}
