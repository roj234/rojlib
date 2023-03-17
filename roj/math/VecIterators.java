package roj.math;

import roj.collect.AbstractIterator;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * 路径迭代算法？
 *
 * @author Roj233
 * @since 2021/9/16 19:36
 */
public final class VecIterators {
	public static final class MH2 extends AbstractIterator<Vec2i> implements Iterable<Vec2i> {
		private final Vec2i hp = new Vec2i();
		public final int x, y, r;
		private final boolean immutable;

		public MH2(int x, int z, int radius, boolean immutable) {
			result = hp;
			this.x = x;
			this.y = z;
			this.r = radius;
			this.immutable = immutable;
		}

		private int s0, dr, d;

		@Override
		@SuppressWarnings("fallthrough")
		public boolean computeNext() {
			Vec2i hp = this.hp;
			if ((s0 & 8) == 0) {
				hp.x = x;
				hp.y = y;
				s0 |= 12;
				dr = 0;
				if (immutable) result = new Vec2i(hp);
				return true;
			}
			if ((s0 & 4) != 0) {
				if (++dr >= r) {
					return false;
				}
				s0 &= 8;
				hp.y = y - dr;
				d = x - dr;
			}
			int r = this.dr, e;

			switch (s0 & 3) {
				case 0:
					e = x + r;
					if (d < e) {
						hp.x = d++;
						break;
					} else {
						s0++;
						hp.x = e;
						d = y - r;
					}
				case 1:
					e = y + r;
					if (d < e) {
						hp.y = d++;
						break;
					} else {
						s0++;
						hp.y = e;
						d = x + r;
					}
				case 2:
					e = x - r;
					if (d > e) {
						hp.x = d--;
						break;
					} else {
						s0++;
						hp.x = e;
						d = y + r;
					}
				case 3:
					e = y - r;
					if (d > e) {
						hp.y = d--;
						break;
					} else {
						s0++;
						return computeNext();
					}
			}
			if (immutable) result = new Vec2i(hp);
			return true;
		}

		@Nonnull
		@Override
		public Iterator<Vec2i> iterator() {
			return this;
		}

		@Override
		public void reset() {
			s0 = 0;
			stage = INITIAL;
		}

		@Override
		public void forEach(Consumer<? super Vec2i> action) {
			if (immutable) {
				Consumer<? super Vec2i> origAct = action;
				action = (Consumer<Vec2i>) v -> origAct.accept(new Vec2i(v));
			}

			Vec2i hp = this.hp;
			hp.x = x;
			hp.y = y;
			action.accept(hp);
			int e1;
			for (int r = 1; r <= this.r; r++) {
				hp.y = y - r;

				e1 = x + r;
				for (int dx = x - r; dx < e1; dx++) {
					hp.x = dx;
					action.accept(hp);
				}
				hp.x = e1;

				e1 = y + r;
				for (int dy = y - r; dy < e1; dy++) {
					hp.y = dy;
					action.accept(hp);
				}
				hp.y = e1;

				e1 = x - r;
				for (int dx = x + r; dx > e1; dx--) {
					hp.x = dx;
					action.accept(hp);
				}
				hp.x = e1;

				e1 = y - r;
				for (int dy = y + r; dy > e1; dy--) {
					hp.y = dy;
					action.accept(hp);
				}
			}
		}
	}
	public static final class MH3 extends AbstractIterator<Vec3i> implements Iterable<Vec3i> {
		private final Vec3i hp = new Vec3i();
		public int x, y, z, r;
		private final boolean immutable;

		public MH3(int x, int y, int z, int r, boolean immutable) {
			result = hp;
			this.x = x;
			this.y = y;
			this.z = z;
			this.r = r;
			this.immutable = immutable;
		}

		@Override
		public boolean computeNext() {
			return false;
		}

		@Nonnull
		@Override
		public Iterator<Vec3i> iterator() {
			throw new UnsupportedOperationException("Not implement yet, use forEach() instead");
			// todo
			//return this;
		}

		@Override
		public void forEach(Consumer<? super Vec3i> c) {
			if (immutable) {
				Consumer<? super Vec3i> origAct = c;
				c = (Consumer<Vec3i>) v -> origAct.accept(new Vec3i(v));
			}

			Vec3i hp = this.hp;
			hp.x = x;
			hp.y = y;
			hp.z = z;
			c.accept(hp);
			int r = 1;
			while (r <= this.r) {
				hp.z = z - r;
				c.accept(hp);
				hp.z = z + r;
				c.accept(hp);

				hp.z = z;
				hp.x = x - r;
				c.accept(hp);
				hp.x = x + r;
				c.accept(hp);

				hp.x = x;
				hp.y = y - r;
				c.accept(hp);
				hp.y = y + r;
				c.accept(hp);

				int r1 = 0;
				while (true) {
					hp.y = y;
					hp.z = z - r;
					fxy(r1, c);
					hp.x = x;
					hp.y = y;
					hp.z = z + r;
					fxy(r1, c);

					if (r1 == r) break;

					hp.x = x - r;
					hp.y = y;
					hp.z = z;
					fyz(r1, c);
					hp.x = x + r;
					hp.y = y;
					hp.z = z;
					fyz(r1, c);

					hp.x = x;
					hp.y = y - r;
					hp.z = z;
					fxz(r1, c);
					hp.x = x;
					hp.y = y + r;
					hp.z = z;
					fxz(r1, c);
					hp.x = x;

					r1++;
				}
				hp.x = x;
				hp.y = y + r;

				for (int z = this.z - r + 1; z < this.z + r; z++) {
					hp.z = z;
					// x- y+
					hp.x = x - r;
					c.accept(hp);
					// x- y-
					hp.y = y - r;
					c.accept(hp);
					// x+ y-
					hp.x = x + r;
					c.accept(hp);
					// x+ y+
					hp.y = y + r;
					c.accept(hp);
				}
				hp.x = x;
				hp.y = y;
				r++;
			}
		}

		private void fxy(int r, Consumer<? super Vec3i> c) {
			Vec3i hp = this.hp;
			hp.y = y - r;

			int e1 = x + r;
			for (int dx = x - r; dx < e1; dx++) {
				hp.x = dx;
				c.accept(hp);
			}
			hp.x = e1;

			e1 = y + r;
			for (int dy = y - r; dy < e1; dy++) {
				hp.y = dy;
				c.accept(hp);
			}
			hp.y = e1;

			e1 = x - r;
			for (int dx = x + r; dx > e1; dx--) {
				hp.x = dx;
				c.accept(hp);
			}
			hp.x = e1;

			e1 = y - r;
			for (int dy = y + r; dy > e1; dy--) {
				hp.y = dy;
				c.accept(hp);
			}
		}

		private void fxz(int r, Consumer<? super Vec3i> c) {
			Vec3i hp = this.hp;
			hp.z = z - r;

			int e1 = x + r;
			for (int dx = x - r; dx < e1; dx++) {
				hp.x = dx;
				c.accept(hp);
			}
			hp.x = e1;

			e1 = z + r;
			for (int dz = z - r; dz < e1; dz++) {
				hp.z = dz;
				c.accept(hp);
			}
			hp.z = e1;

			e1 = x - r;
			for (int dx = x + r; dx > e1; dx--) {
				hp.x = dx;
				c.accept(hp);
			}
			hp.x = e1;

			e1 = z - r;
			for (int dz = z + r; dz > e1; dz--) {
				hp.z = dz;
				c.accept(hp);
			}
		}

		private void fyz(int r, Consumer<? super Vec3i> c) {
			Vec3i hp = this.hp;
			hp.y = y - r;

			int e1 = z + r;
			for (int dz = z - r; dz < e1; dz++) {
				hp.z = dz;
				c.accept(hp);
			}
			hp.z = e1;

			e1 = y + r;
			for (int dy = y - r; dy < e1; dy++) {
				hp.y = dy;
				c.accept(hp);
			}
			hp.y = e1;

			e1 = z - r;
			for (int dz = z + r; dz > e1; dz--) {
				hp.z = dz;
				c.accept(hp);
			}
			hp.z = e1;

			e1 = y - r;
			for (int dy = y + r; dy > e1; dy--) {
				hp.y = dy;
				c.accept(hp);
			}
		}
	}
	public static final class BORDER extends Rect3i implements Iterable<Vec3i>, Iterator<Vec3i> {
		private final Vec3i hp = new Vec3i();
		private int x, y, z;
		private final boolean immutable;

		public BORDER(Rect3i rect, boolean immutable) {
			super(rect);
			this.immutable = immutable;
		}

		public BORDER(int x1, int y1, int z1, int x2, int y2, int z2, boolean immutable) {
			super(x1, y1, z1, x2, y2, z2);
			this.immutable = immutable;
		}

		@Nonnull
		@Override
		public Iterator<Vec3i> iterator() {
			reset();
			return this;
		}

		private void reset() {
			x = xmin - 1;
			y = ymin;
			z = zmin;
		}

		@Override
		public boolean hasNext() {
			return x < xmax || y < ymax || z < zmax;
		}

		@Override
		public Vec3i next() {
			if (z == zmin || z == zmax || y == ymin || y == ymax) x++;
			else x += xmax - xmin;
			if (x > xmax) {
				x = xmin;
				if (z == zmin || z == zmax) y++;
				else y += ymax - ymin;

				if (y > ymax) {
					y = ymin;
					z++;
				}
			}
			hp.set(x, y, z);
			return immutable ? new Vec3i(hp) : hp;
		}
	}

	/**
	 * <pre>
	 *  路径迭代？
	 *  ====================>
	 * /|\                  |
	 *  |                   |
	 *  |                   |
	 *  |         C         |
	 *  |                   |
	 *  |                  \|/
	 *  <====================
	 */
	public static MH2 mhIterator2D(int x, int z, int radius) {
		return new MH2(x, z, radius, false);
	}
	public static MH3 mhIterator3D(int x, int y, int z, int radius) {
		return new MH3(x, y, z, radius, false);
	}
	public static BORDER borderIterator3D(Rect3i rect) {
		return new BORDER(rect, false);
	}
}
