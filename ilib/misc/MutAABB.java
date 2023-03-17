package ilib.misc;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * @author Roj233
 * @since 2022/5/6 17:16
 */
//!!AT [["net.minecraft.util.math.AxisAlignedBB", ["*"]]]
public final class MutAABB extends AxisAlignedBB {
	public MutAABB() {
		super(0, 0, 0, 0, 0, 0);
	}

	public void set0(double a, double b, double c, double d, double e, double f) {
		minX = a;
		minY = b;
		minZ = c;
		maxX = d;
		maxY = e;
		maxZ = f;
	}

	public void set0(AxisAlignedBB box) {
		minX = box.minX;
		minY = box.minY;
		minZ = box.minZ;
		maxX = box.maxX;
		maxY = box.maxY;
		maxZ = box.maxZ;
	}

	public void set0(AxisAlignedBB box, double a, double b, double c, double d, double e, double f) {
		box.minX = a;
		box.minY = b;
		box.minZ = c;
		box.maxX = d;
		box.maxY = e;
		box.maxZ = f;
	}

	public void grow0(double size) {
		minX -= size;
		minY -= size;
		minZ -= size;
		maxX += size;
		maxY += size;
		maxZ += size;
	}

	public void grow0(double x, double y, double z) {
		minX -= x;
		minY -= y;
		minZ -= z;
		maxX += x;
		maxY += y;
		maxZ += z;
	}

	public void grow0(BlockPos pos, double size) {
		minX = (pos.getX() - size);
		minY = (pos.getY() - size);
		minZ = (pos.getZ() - size);
		maxX = (pos.getX() + size);
		maxY = (pos.getY() + size);
		maxZ = (pos.getZ() + size);
	}

	public AxisAlignedBB grow0(AxisAlignedBB box, double x, double y, double z) {
		set0(box);
		grow0(x, y, z);
		return this;
	}

	public MutAABB offset0(AxisAlignedBB box, double x, double y, double z) {
		set0(box);
		offset0(x, y, z);
		return this;
	}

	public void offset0(double x, double y, double z) {
		minX += x;
		minY += y;
		minZ += z;
		maxX += x;
		maxY += y;
		maxZ += z;
	}

	public MutAABB expand0(double x, double y, double z) {
		double x0 = this.minX;
		double y0 = this.minY;
		double z0 = this.minZ;
		double x1 = this.maxX;
		double y1 = this.maxY;
		double z1 = this.maxZ;
		if (x < 0.0D) {
			x0 += x;
		} else if (x > 0.0D) {
			x1 += x;
		}

		if (y < 0.0D) {
			y0 += y;
		} else if (y > 0.0D) {
			y1 += y;
		}

		if (z < 0.0D) {
			z0 += z;
		} else if (z > 0.0D) {
			z1 += z;
		}
		set0(x0, y0, z0, x1, y1, z1);
		return this;
	}

	public AxisAlignedBB copy() {
		return grow(0);
	}
}
