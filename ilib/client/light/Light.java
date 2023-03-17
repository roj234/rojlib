package ilib.client.light;

import ilib.misc.MutAABB;
import roj.math.Vec3f;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * @author Roj233
 * @since 2022/5/17 3:23
 */
public class Light extends Vec3f {
	public float r, g, b, a;
	public float radius;

	public Light() {}

	public Light(float x, float y, float z, float r, float g, float b, float a, float radius) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
		this.radius = radius;
	}

	public void getBoundBox(MutAABB box) {
		box.set0(x, y, z, x, y, z);
		box.grow0(radius);
	}

	public Light pos(BlockPos pos) {
		return pos(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
	}

	public Light pos(Vec3d pos) {
		return pos(pos.x, pos.y, pos.z);
	}

	public Light pos(Entity e) {
		return pos(e.posX, e.posY, e.posZ);
	}

	public Light pos(double x, double y, double z) {
		return pos((float) x, (float) y, (float) z);
	}

	public Light pos(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	public Light color(int rgb, boolean hasAlpha) {
		a = hasAlpha ? ((rgb >>> 24) & 0xFF) / 255f : 1;
		r = ((rgb >>> 16) & 0xFF) / 255f;
		g = ((rgb >>> 8) & 0xFF) / 255f;
		b = (rgb & 0xFF) / 255f;
		return this;
	}

	public Light color(float r, float g, float b) {
		return color(r, g, b, 1f);
	}

	public Light color(float r, float g, float b, float a) {
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
		return this;
	}

	public Light radius(float radius) {
		this.radius = radius;
		return this;
	}
}
