package roj.renderer.util;

import roj.collect.ArrayList;
import roj.collect.IterablePriorityQueue;

import java.util.List;

import static roj.math.MathUtils.nextPowerOfTwo;

/**
 * @author Roj233
 * @since 2022/5/20 0:01
 */
public class Stitcher {
	private final IterablePriorityQueue<Holder> tiles = new IterablePriorityQueue<>();
	private Slot freeHead, freeTail;
	private Slot placed;

	private int width, height;

	private final int mipmap;
	private final int maxWidth, maxHeight, maxTileSize;
	private boolean allowRotate;

	public Stitcher(int maxW, int maxH, int maxDim, int mipmap) {
		this.mipmap = mipmap;
		maxWidth = maxW;
		maxHeight = maxH;
		maxTileSize = maxDim;
	}

	public void add(Tile tile) {
		float scale = 1;
		int max = maxTileSize;
		if (max > 0 && (tile.getTileWidth() > max || tile.getTileHeight() > max)) {
			scale = max / (float) Math.max(tile.getTileWidth(), tile.getTileHeight());
		}

		int w = (int) (tile.getTileWidth() * scale);
		int h = (int) (tile.getTileHeight() * scale);
		int mipmapSize = 1 << mipmap;
		if (mipmapSize > 1) {
			w = (w + mipmapSize-1) & -mipmapSize;
			h = (h + mipmapSize-1) & -mipmapSize;
		}

		tiles.add(new Holder(tile, w, h));
	}

	public void stitch() {
		width = 0;
		height = 0;

		placed = freeHead = freeTail = null;

		Object[] array = tiles.array();
		for (int i = 0; i < tiles.size(); i++) {
			var h = (Holder) array[i];
			if (!place(h)) {
				for (int j = 0; j < i; j++) {
					System.out.println("  " + array[j]);
				}
				Tile tile = h.getTile();
				throw new RuntimeException(String.format("无法置入: %s - 大小: %dx%d", tile.getTileName(), tile.getTileWidth(), tile.getTileHeight()));
			}
		}
		//holders.clear();

		width = nextPowerOfTwo(width);
		height = nextPowerOfTwo(height);

		Slot slot = placed;
		placed = null;

		float u = 1f / width;
		float v = 1f / height;
		while (slot != null) {
			var _tile = slot._tile;
			_tile.getTile().onStitched(width, height, u, v, slot.x, slot.y, slot.width, slot.height, _tile.isRotated());

			slot = slot.next;
		}
	}

	public int getWidth() {return width;}
	public int getHeight() {return height;}
	public List<Tile> getTiles() {
		Object[] array = tiles.array();
		List<Tile> list = new ArrayList<>(tiles.size());
		for (int i = 0; i < tiles.size(); i++) list.add(((Holder)array[i]).getTile());
		return list;
	}

	private boolean place(Holder tile) {
		// Slot.next是一个链表, 否则remove浪费
		Slot prev = null, free = freeHead;
		var remainSpace = new ArrayList<Slot>();

		while (free != null) {
			// place成功则next就被修改了 (side effect)
			Slot next = free.next;

			remainSpace.clear();
			if (free.place(tile, this, remainSpace)) {
				// unlink
				if (prev == null) freeHead = next;
				else prev.next = next;

				// free恰好是最后一个
				if (freeTail == free) freeTail = prev;

				addFree(remainSpace);
				return true;
			}
			prev = free;
			free = next;
		}

		return allocate(tile);
	}

	private boolean allocate(Holder tile) {
		int tileW = nextPowerOfTwo(tile.getWidth());
		int tileH = nextPowerOfTwo(tile.getHeight());
		int min2 = Math.min(tileW, tileH);

		int w = nextPowerOfTwo(width);
		int h = nextPowerOfTwo(height);
		int w1 = nextPowerOfTwo(width + min2);
		int h1 = nextPowerOfTwo(height + min2);
		if (w1 > maxWidth || h1 > maxHeight) return false;

		boolean expandedW = w != w1;
		boolean expandedH = h != h1;
		// 优先扩大width
		boolean expandWidth = expandedW ^ expandedH ? !expandedW : w <= h;

		Slot slot;
		if (expandWidth) {
			if (allowRotate && tile.getWidth() > tile.getHeight()) {
				tile.rotate();

				if (tileW > tileH) {
					int tmp = tileH;
					tileH = tileW;
					tileW = tmp;
				}
			}

			// first tile
			if (height == 0) height = tileH;

			// 为了避免浪费空间，Holder要预先排序
			// (不支持把多个未使用的Slot合并)
			slot = new Slot(width, 0, tileW, height);

			this.width += tileW;
		} else {
			slot = new Slot(0, height, width, tileH);

			this.height += tileH;
		}

		ArrayList<Slot> remain = new ArrayList<>();
		if (!slot.place(tile, this, remain)) throw new AssertionError();
		addFree(remain);
		return true;
	}

	private void addFree(List<Slot> result) {
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

	final void addPlaced(Slot slot) {
		slot.next = placed;
		placed = slot;
	}

	private static final class Slot {
		final char x, y;
		char width, height;
		Holder _tile;

		Slot next;

		Slot(int x, int y, int w, int h) {
			this.x = (char) x;
			this.y = (char) y;
			width = (char) w;
			height = (char) h;
		}

		public boolean place(Holder tex, Stitcher owner, List<Slot> remainSpace) {
			int w = tex.getWidth();
			int h = tex.getHeight();
			checkSizeAndRotate:
			if (w > width || h > height) {
				// 隐含 w != h
				if (owner.allowRotate && h <= width && w <= height) {
					tex.rotate();
					break checkSizeAndRotate;
				}
				return false;
			}

			if (w == width && h == height) {
				this._tile = tex;
				owner.addPlaced(this);
				return true;
			}

			int rw = width - w, rh = height - h;
			if (rh > 0 && rw > 0) {
				int ww = Math.max(height, rw);
				int hh = Math.max(width, rh);
				if (ww >= hh) {
					remainSpace.add(new Slot(x, y + h, w, rh));
					remainSpace.add(new Slot(x + w, y, rw, height));
				} else {
					remainSpace.add(new Slot(x + w, y, rw, h));
					remainSpace.add(new Slot(x, y + h, width, rh));
				}
			} else if (rw == 0) {
				remainSpace.add(new Slot(x, y + h, w, rh));
			} else {
				remainSpace.add(new Slot(x + w, y, rw, h));
			}

			var used = new Slot(x, y, w, h);
			used._tile = tex;
			owner.addPlaced(used);
			return true;
		}
	}

	public static final class Holder implements Comparable<Holder> {
		private final Tile tile;
		private boolean rotated;
		private final char w, h;

		Holder(Tile tile, int w, int h) {
			this.tile = tile;
			//rotated = h > w;
			this.w = (char) w;
			this.h = (char) h;
		}

		public Tile getTile() {return tile;}
		public int getWidth() {return rotated ? h : w;}
		public int getHeight() {return rotated ? w : h;}
		public boolean isRotated() {return rotated;}

		void rotate() {rotated = !rotated;}

		public String toString() {return '{'+tile.getTileName()+'@'+tile.getTileWidth()+'x'+tile.getTileHeight() + (w != tile.getTileWidth() || h != tile.getTileHeight() ? " (as "+w+'x'+h+')' : "")+'}';}

		public int compareTo(Holder h) {
			int a, b;

			a = getWidth();
			b = h.getWidth();
			if (a != b) return a < b ? 1 : -1;

			a = getHeight();
			b = h.getHeight();
			if (a != b) return a < b ? 1 : -1;

			String n = tile.getTileName();
			String n1 = h.tile.getTileName();
			if (n == null) return n1 == null ? 0 : -1;
			return n.compareTo(n1);
		}
	}
}
