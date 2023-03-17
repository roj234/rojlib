package roj.math;

/**
 * @author Roj234
 * @since 2022/1/14 14:16
 */
public class Spherei {
	public int x, y, z;
	public int radius;

	public Spherei(Vec3i v, int radius) {
		this.x = v.x;
		this.y = v.y;
		this.z = v.z;
		this.radius = radius;
	}

	public Spherei(int x, int y, int z, int radius) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.radius = radius;
	}

	public double volume() {
		return Math.PI * 4.0 / 3.0 * radius * radius * radius;
	}

	public Vec3i center() {
		return new Vec3i(x, y, z);
	}

	public boolean contains(Vec3i v) {
		return radius * radius < distance(v.x, v.y, v.z);
	}

	public boolean contains(int x, int y, int z) {
		return radius * radius < distance(x, y, z);
	}

	public boolean contains(Spherei o) {
		return o.x + o.radius <= x + radius && o.x - o.radius >= x - radius &&

			o.y + o.radius <= y + radius && o.y - o.radius >= y - radius &&

			o.z + o.radius <= z + radius && o.z - o.radius >= z - radius;
	}

	public boolean intersects(Spherei o) {
		int sd2 = o.radius + radius;
		return sd2 * sd2 > distance(o.x, o.y, o.z);
	}

	public int distance(int x, int y, int z) {
		int dx = this.x - x;
		int dy = this.y - y;
		int dz = this.z - z;
		return dx * dx + dy * dy + dz * dz;
	}
}
