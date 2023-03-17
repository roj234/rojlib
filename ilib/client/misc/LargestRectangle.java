package ilib.client.misc;

import roj.collect.*;
import roj.math.Rect2i;

import java.util.Arrays;
import java.util.List;

public class LargestRectangle {
	public static void main(String[] args) {
		LargestRectangle instance = new LargestRectangle();

		//String points = "0,0 0,1 0,2 0,3 0,4 0,5 1,1 1,2 1,3 1,4 1,5 1,6 2,4 2,5 2,6";
		String points = args[0];
		Arrays.stream(points.split(" ")).map((s) -> s.split(",")).forEach((a) -> {
			instance.addQuad(Integer.parseInt(a[0]), Integer.parseInt(a[1]));
		});

		List<Quad> quads = instance.compute();
		System.out.println("最小覆盖矩形：" + quads);
	}

	private static final class Quad extends Rect2i {
		int pos;

		public Quad(int pos) {
			this.pos = pos;
			reset();
		}

		public void reset() {
			xmin = xmax = pos >>> 16;
			ymin = ymax = pos & 0xFFFF;
		}
	}

	private final IntSet quads = new IntSet();
	private final SimpleList<Quad> cycle = new SimpleList<>();
	private final BSLowHeap<Quad> finish = new BSLowHeap<>((o1, o2) -> {
		int v = Integer.compare(o1.xmax - o1.xmin, o2.xmax - o2.xmin);
		if (v != 0) return v;
		v = Integer.compare(o1.ymax - o1.ymin, o2.ymax - o2.ymin);
		if (v != 0) return v;

		// for identity
		v = Integer.compare(o1.xmin, o2.xmin);
		if (v != 0) return v;
		return Integer.compare(o1.ymin, o2.ymin);
	});

	private final RingBuffer<Quad> cache = new RingBuffer<>(100, false);

	public void addQuad(int x, int y) {
		quads.add(key(x, y));
	}

	public List<Quad> compute() {
		cycle.clear();
		finish.clear();
		SimpleList<Quad> result = new SimpleList<>();

		for (IntIterator itr = quads.iterator(); itr.hasNext(); ) {
			Quad box = allocate(itr.nextInt());

			if (tryGrow(box)) {cycle.add(box);} else finish.add(box);
		}

		do {
			do {
				for (int i = cycle.size() - 1; i >= 0; i--) {
					Quad box = cycle.get(i);
					if (!tryGrow(box)) {
						// noinspection all
						if (!finish.add(box)) {
							reserve(box);
						}
						cycle.remove(i);
					}
				}
			} while (!cycle.isEmpty());

			Quad best = finish.remove(0);
			result.add(best);

			removeRange(best.xmin, best.xmax, best.ymin, best.ymax);

			for (int i = 0; i < finish.size(); i++) {
				Quad box = finish.get(i);
				if (best.intersects(box)) {
					reserve(box);
				} else {
					cycle.add(box);
				}
			}
			finish.clear();

			addNearby(best);
		} while (!quads.isEmpty());

		return result;
	}

	private void addNearby(Quad box) {
		IntSet quads1 = quads;
		SimpleList<Quad> cycle1 = cycle;

		int y = box.ymin - 1;
		int x = box.xmin;
		while (x <= box.xmax) {
			int pos = key(x, y);
			if (quads1.contains(pos)) {
				cycle1.add(allocate(pos));
			}
			x++;
		}

		y = box.ymin + 1;
		x = box.xmin;
		while (x <= box.xmax) {
			int pos = key(x, y);
			if (quads1.contains(pos)) {
				cycle1.add(allocate(pos));
			}
			x++;
		}

		y = box.ymin;
		x = box.xmin + 1;
		while (y <= box.ymax) {
			int pos = key(x, y);
			if (quads1.contains(pos)) {
				cycle1.add(allocate(pos));
			}
			y++;
		}

		y = box.ymin;
		x = box.xmin - 1;
		while (y <= box.ymax) {
			int pos = key(x, y);
			if (quads1.contains(pos)) {
				cycle1.add(allocate(pos));
			}
			y++;
		}
	}

	private void removeRange(int xmin, int xmax, int ymin, int ymax) {
		while (xmin <= xmax) {
			int y = ymin;
			while (y <= ymax) {
				if (!quads.remove(key(xmin, y))) throw new IllegalStateException("Not a valid point at " + xmin + ", " + y);
				y++;
			}
			xmin++;
		}
	}

	private boolean tryGrow(Quad box) {
		boolean grown = false;
		if (canGrow(box.xmin, box.xmax, box.ymin, box.ymax + 1)) {
			box.ymax++;
			grown = true;
		}
		if (canGrow(box.xmin, box.xmax, box.ymin - 1, box.ymax)) {
			box.ymin--;
			grown = true;
		}
		if (canGrow(box.xmin, box.xmax + 1, box.ymin, box.ymax)) {
			box.xmax++;
			grown = true;
		}
		if (canGrow(box.xmin - 1, box.xmax, box.ymin, box.ymax)) {
			box.xmin--;
			grown = true;
		}
		return grown;
	}

	private boolean canGrow(int xmin, int xmax, int ymin, int ymax) {
		while (xmin <= xmax) {
			int y = ymin;
			while (y <= ymax) {
				if (!quads.contains(key(xmin, y))) return false;
				y++;
			}
			xmin++;
		}
		return true;
	}

	private Quad allocate(int pos) {
		Quad quad = cache.pollLast();
		if (quad == null) return new Quad(pos);
		quad.pos = pos;
		quad.reset();
		return quad;
	}

	private void reserve(Quad q) {
		cache.ringAddLast(q);
	}

	private static int key(int x, int y) {
		return ((x << 16) & 0xFFFF0000) | (y & 0xFFFF);
	}
}
