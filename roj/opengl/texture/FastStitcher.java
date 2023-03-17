package roj.opengl.texture;

import roj.collect.BSLowHeap;
import roj.collect.SimpleList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.math.MathUtils.getMin2PowerOf;

/**
 * @author Roj233
 * @since 2022/5/20 0:01
 */
public class FastStitcher {
	private final BSLowHeap<Holder> holders = new BSLowHeap<>(null);
	private Slot freeHead, freeTail;
	private Slot completed;

	private int width, height;

	private final int mipmap;
	private final int maxWidth, maxHeight;

	private final int tileMaxWH;
	private boolean allowRotate, reusable;

	public FastStitcher(int maxW, int maxH, int maxDim, int mipmap) {
		System.out.println("FastStitcher:30: mipmap="+mipmap);
		this.mipmap = mipmap;
		maxWidth = maxW;
		maxHeight = maxH;
		tileMaxWH = maxDim;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public void addSprite(IAtlasPiece img) {
		holders.add(new Holder(img, mipmap, tileMaxWH));
	}

	public void stitch() {
		width = 0;
		height = 0;

		completed = freeHead = freeTail = null;

		for (int i = 0; i < holders.size(); i++) {
			Holder h = holders.get(i);
			if (!fitIn(h)) {
				for (int j = 0; j < i; j++) {
					System.out.println("  " + holders.get(j));
				}
				IAtlasPiece img = h.getAtlasSprite();
				throw new RuntimeException(String.format("无法置入: %s - 大小: %dx%d", img.getPieceName(), img.getPieceWidth(), img.getPieceHeight()));
			}
		}
		//holders.clear();

		width = getMin2PowerOf(width);
		height = getMin2PowerOf(height);

		Slot slot = completed;
		completed = null;

		float u = 1f / width;
		float v = 1f / height;
		while (slot != null) {
			Holder tex = slot.holder();
			tex.getAtlasSprite().onStitched(width, height, u, v, slot.getX(), slot.getY(), slot.width, slot.height, tex.isRotated());

			slot = slot.next;
		}
	}

	public List<IAtlasPiece> getRegisteredTextures() {
		List<IAtlasPiece> list = new SimpleList<>(holders.size());
		for (int i = 0; i < holders.size(); i++) list.add(holders.get(i).getAtlasSprite());
		return list;
	}

	private static int getMipmapDimension(int wh, int level) {
		return (wh >> level) + ((wh & (1 << level) - 1) == 0 ? 0 : 1) << level;
	}

	private boolean fitIn(Holder tex) {
		// Slot.next是一个链表, 否则remove浪费
		Slot prev = null, free = freeHead;
		int mipmap = this.mipmap;

		while (free != null) {
			// tryUse成功则next就被修改了 (side effect)
			Slot next = free.next;

			List<Slot> result = free.tryUse(tex, this);
			if (result != null) {
				// unlink
				if (prev == null) {
					freeHead = next;
				} else {
					prev.next = next;
				}

				if (freeTail == free) {
					// free恰好是最后一个
					freeTail = prev;
				}

				linkin(result);
				return true;
			}
			prev = free;
			free = next;
		}

		return allocate(tex);
	}

	private boolean allocate(Holder tex) {
		int texW = getMin2PowerOf(tex.getWidth());
		int texH = getMin2PowerOf(tex.getHeight());
		int min2 = Math.min(texW, texH);

		int w = getMin2PowerOf(width);
		int h = getMin2PowerOf(height);
		int w1 = getMin2PowerOf(width + min2);
		int h1 = getMin2PowerOf(height + min2);
		if (w1 > maxWidth || h1 > maxHeight) return false;

		boolean expandedW = w != w1;
		boolean expandedH = h != h1;
		// 优先扩大width
		boolean expandWidth = expandedW ^ expandedH ? !expandedW : w <= h;

		Slot slot;
		if (expandWidth) {
			if (tex.getWidth() > tex.getHeight()) {
				tex.rotate();

				if (texW > texH) {
					int tmp = texH;
					texH = texW;
					texW = tmp;
				}
			}

			// first tex
			if (height == 0) height = texH;

			// 为了避免浪费空间，Holder要预先排序
			// (不支持把多个未使用的Slot合并)
			slot = new Slot(width, 0, texW, height);

			this.width += texW;
		} else {
			slot = new Slot(0, height, width, texH);

			this.height += texH;
		}

		List<Slot> remain = slot.tryUse(tex, this);
		if (remain == null) throw new AssertionError();
		linkin(remain);
		return true;
	}

	private void linkin(List<Slot> result) {
		if (result.isEmpty()) return;
		int i = 0;
		Slot tail = freeTail;
		if (tail == null) tail = freeHead = result.get(i++);

		for (; i < result.size(); i++) {
			tail.next = result.get(i);
			tail = tail.next;
		}
		freeTail = tail;
	}

	final void completed(Slot slot) {
		slot.next = completed;
		completed = slot;
	}

	private static final class Slot {
		private final char x, y;
		private final char width, height;
		private Holder tex;

		Slot next;

		Slot(int x, int y, int w, int h) {
			this.x = (char) x;
			this.y = (char) y;
			width = (char) w;
			height = (char) h;
		}

		public Holder holder() {
			return tex;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public List<Slot> tryUse(Holder tex, FastStitcher used) {
			int w = tex.getWidth();
			int h = tex.getHeight();
			checkSizeAndRotate:
			if (w > width || h > height) {
				// 隐含 w != h
				if (used.allowRotate && h <= width && w <= height) {
					tex.rotate();
					break checkSizeAndRotate;
				}
				return null;
			}

			if (w == width && h == height) {
				this.tex = tex;
				used.completed(this);
				return Collections.emptyList();
			}

			List<Slot> remainSpace;
			int rw = width - w, rh = height - h;
			if (rh > 0 && rw > 0) {
				int ww = Math.max(height, rw);
				int hh = Math.max(width, rh);
				remainSpace = Arrays.asList(new Slot[2]);
				if (ww >= hh) {
					remainSpace.set(0, new Slot(x, y + h, w, rh));
					remainSpace.set(1, new Slot(x + w, y, rw, height));
				} else {
					remainSpace.set(0, new Slot(x + w, y, rw, h));
					remainSpace.set(1, new Slot(x, y + h, width, rh));
				}
			} else if (rw == 0) {
				remainSpace = Collections.singletonList(new Slot(x, y + h, w, rh));
			} else if (rh == 0) {
				remainSpace = Collections.singletonList(new Slot(x + w, y, rw, h));
			} else
				throw new AssertionError();

			Slot usedSpace = new Slot(x, y, w, h);
			usedSpace.tex = tex;
			used.completed(usedSpace);
			return remainSpace;
		}

		public String toString() {
			return "{x=" + (int)x + ", y=" + (int)y + ", w=" + (int)width + ", h=" + (int)height + ", tex=" + tex + '}';
		}
	}

	public static class Holder implements Comparable<Holder> {
		private final IAtlasPiece tex;
		private boolean rotated;
		private final char useW, useH;

		public Holder(IAtlasPiece tex, int mipmap, int maxWH) {
			this.tex = tex;
			float scale = 1;
			if (maxWH > 0 && tex.getPieceWidth() > maxWH && tex.getPieceHeight() > maxWH) {
				scale = maxWH / (float) Math.min(tex.getPieceWidth(), tex.getPieceHeight());
			}
			int h = getMipmapDimension((int) (tex.getPieceHeight() * scale), mipmap);
			int w = getMipmapDimension((int) (tex.getPieceWidth() * scale), mipmap);
			rotated = h > w;
			useW = (char) w;
			useH = (char) h;
		}

		public IAtlasPiece getAtlasSprite() {
			return tex;
		}

		public int getWidth() {
			return rotated ? useH : useW;
		}

		public int getHeight() {
			return rotated ? useW : useH;
		}

		public void rotate() {
			rotated = !rotated;
		}

		public boolean isRotated() {
			return rotated;
		}

		public String toString() {
			return "{" + tex.getPieceName() + "@" + tex.getPieceWidth() + "x" + tex.getPieceHeight() + '}';
		}

		public int compareTo(Holder h) {
			int a = tex.getPieceHeight();
			int b = h.tex.getPieceHeight();
			if (a != b) return a < b ? 1 : -1;

			a = tex.getPieceWidth();
			b = h.tex.getPieceWidth();
			if (a != b) return a < b ? 1 : -1;

			String n = tex.getPieceName();
			String n1 = h.tex.getPieceName();
			if (n == null) return n1 == null ? 0 : -1;
			return n.compareTo(n1);
		}
	}
}
