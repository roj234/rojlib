package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.collect.Octree.Node;
import roj.collect.Octree.OctreeEntry;
import roj.math.MathUtils;
import roj.math.Vec3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author solo6975
 * @since 2022/1/16 6:04
 */
public class Octree<V extends OctreeEntry> implements _Generic_Map<Node>, Iterable<Node> {
	public interface OctreeEntry {
		Vec3i getPos();
	}

	public static final class Node implements _Generic_Entry {
		int k;
		Object v;
		byte mask;

		Node(int k) {this.k = k;}

		public int getKey() { return k; }
		public Object getValue() { return v; }

		public int getMask() { return mask & 0xFF; }

		@Override
		public String toString() { return "0b"+Integer.toBinaryString(k)+" @ "+v; }

		Node next;
		@Override
		public Node __next() { return next; }
	}

	/**
	 * {@link #init(int, int, int) init(unit = 1024, maxDepth = 10, capacity = 16)}
	 */
	public Octree() {init(1024, 10, 16);}
	public Octree(int unit) {init(unit, 10, 16);}
	public Octree(int unit, int capacity) {init(unit, 10, capacity);}

	private int unit, maxDepth, vCount;

	public int unit() {return unit;}
	public int size() {return vCount;}
	@Deprecated
	public int maxDepth() {return maxDepth;}

	/**
	 * 使用int存储位置，flag1位，每层3bit，最大支持10层，默认unit为 1 << 10 = 1024 <br>
	 * 补充说明： 此方法会重置此树
	 *
	 * @param unit 分割起点（大正方体的边长的一半） 建议：(对于均匀的点集)点分布的半径
	 * @param maxDepth 最大分割级别，超过此级别退化为List，不能超过10 建议: 10
	 * @param capacity 初始化容量
	 *
	 * @see Node#k node偏移量
	 * @see #clear()
	 */
	@Deprecated
	public void init(int unit, int maxDepth, int capacity) {
		// not empty
		if (nodes != null) {
			Arrays.fill(nodes, null);
			vCount = 0;
			size = 0;
		}
		if (unit <= 1) throw new IllegalArgumentException();
		this.unit = MathUtils.getMin2PowerOf(unit);
		if (maxDepth <= 1 || maxDepth > 10) throw new IllegalArgumentException();
		this.maxDepth = maxDepth;
		int mask1 = MathUtils.getMin2PowerOf(capacity) - 1;
		if (nodes != null) {
			if (mask < mask1) {
				nodes = null;
				mask = mask1;
			}
		} else {
			mask = mask1;
		}
	}

	public void clear() {
		vCount = 0;
		size = 0;
		if (nodes != null) Arrays.fill(nodes, null);
	}

	@SuppressWarnings("unchecked")
	public boolean add(V v) {
		if (size > mask * 1.5f) {
			mask = ((mask + 1) << 1) - 1;
			resize();
		}

		Vec3i key = v.getPos();
		int cx = 0, cy = 0, cz = 0;

		int level = 0;
		int next = 0b1000;
		Node node = null;
		do {
			if (key.x < cx) {
				next += 4;
				cx -= unit >>> level;
			} else {
				cx += unit >>> level;
			}
			if (key.y < cy) {
				next += 2;
				cy -= unit >>> level;
			} else {
				cy += unit >>> level;
			}
			if (key.z < cz) {
				next += 1;
				cz -= unit >>> level;
			} else {
				cz += unit >>> level;
			}
			Node prev = node;
			node = getOrCreateEntry(next);
			// TODO 树不需要满，而是等到元素超过一个阈值之后再拆
			if (node.v == null) {
				if (prev != null) // may be first node [0b1xxx]
				// 看上面I_XXX的定义
				{prev.mask |= 1 << (next & 7);}
				size++;
				vCount++;
				node.v = v;
				return true;
			}
			if (++level == maxDepth) break;

			Vec3i vPos = ((V) node.v).getPos();
			if (key.equals(vPos)) {
				node.v = v;
				return false;
			}

			// 每个node的pos都是离原点最近的，方便gets的筛选
			// 也方便删除后的重构
			if (key.lengthSquared() < vPos.lengthSquared()) {
				V v0 = (V) node.v;
				node.v = v;
				v = v0;
				key = v.getPos();
			}

			next <<= 3;
		} while (true);

		ArrayList<V> list;
		// 不能再分了，到了十级，就退化成List好了
		if (node.v instanceof ArrayList) {
			list = (ArrayList<V>) node.v;
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).getPos().equals(key)) {
					list.set(i, v);
					return false;
				}
			}
		} else {
			list = new ArrayList<>(3);
			V v1 = (V) node.v;
			node.v = list;
			list.add(v1);
			if (v1.getPos().equals(key)) {
				return false;
			}
		}
		vCount++;
		list.add(v);
		return true;
	}

	private static int getNearest(Vec3i pos, int sunit) {
		int cx = 0, cy = 0, cz = 0;

		int next = 0b1000;
		do {
			if (pos.x < cx) {
				next += 4;
				cx -= sunit;
			} else {
				cx += sunit;
			}
			if (pos.y < cy) {
				next += 2;
				cy -= sunit;
			} else {
				cy += sunit;
			}
			if (pos.z < cz) {
				next += 1;
				cz -= sunit;
			} else {
				cz += sunit;
			}

			next <<= 3;
			sunit >>>= 1;
		} while (sunit > 0 && (0 == (next & (1 << 31))));

		return next;
	}

	/**
	 * 移动一个node
	 */
	public boolean move(Vec3i pos, Vec3i to) {

		return true;
	}

	/**
	 * 获取位置相等的Value
	 *
	 * @see #getSome(Vec3i, float, float, LocationSet) 获取一些Value
	 */
	@SuppressWarnings("unchecked")
	public V getExact(Vec3i key) {
		int cx = 0, cy = 0, cz = 0;

		int sunit = unit;
		int next = 0b1000;
		Node node;
		do {
			if (key.x < cx) {
				next += 4;
				cx -= sunit;
			} else {
				cx += sunit;
			}
			if (key.y < cy) {
				next += 2;
				cy -= sunit;
			} else {
				cy += sunit;
			}
			if (key.z < cz) {
				next += 1;
				cz -= sunit;
			} else {
				cz += sunit;
			}
			node = getEntry(next);
			if (node == null) return null;
			if (node.v.getClass() == ArrayList.class) break;
			sunit >>>= 1;

			Vec3i vPos = ((V) node.v).getPos();
			if (key.equals(vPos)) {
				return (V) node.v;
			}
			next <<= 3;
		} while (true);

		ArrayList<V> list = (ArrayList<V>) node.v;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).getPos().equals(key)) return list.get(i);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public V remove(Vec3i key) {
		int cx = 0, cy = 0, cz = 0;

		int sunit = unit;
		int next = 0b1000;
		Node node = null;
		do {
			if (key.x < cx) {
				next += 4;
				cx -= sunit;
			} else {
				cx += sunit;
			}
			if (key.y < cy) {
				next += 2;
				cy -= sunit;
			} else {
				cy += sunit;
			}
			if (key.z < cz) {
				next += 1;
				cz -= sunit;
			} else {
				cz += sunit;
			}
			Node prev = node;
			node = getEntry(next);
			if (node == null) return null;
			if (node.v instanceof ArrayList) break;

			Vec3i vPos = ((V) node.v).getPos();
			if (key.equals(vPos)) {
				// I got you!
				V v = (V) node.v;
				if (node.mask == 0) {
					if (prev != null) prev.mask ^= 1 << (next & 7);
					removeNode(node);
				} else {
					if (!graft(node)) {
						if (prev != null) prev.mask ^= 1 << (next & 7);
					}
				}
				vCount--;
				return v;
			}

			sunit >>>= 1;
			next <<= 3;
		} while (true);

		ArrayList<V> list = (ArrayList<V>) node.v;
		for (int i = 0; i < list.size(); i++) {
			V v = list.get(i);
			if (v.getPos().equals(key)) {
				list.remove(i);
				// 此node不可能有child
				return v;
			}
		}
		return null;
	}

	/**
	 * 在删除元素后保证树还符合 [任意节点的坐标都比其孩子节点的更靠近原点] 的规则
	 */
	@SuppressWarnings("unchecked")
	private boolean graft(Node n) {
		int key = n.k << 3;
		int mask = n.mask & 0xFF;
		int j = 1;

		int listId = -1;
		int n1Dist = Integer.MAX_VALUE;
		Node n1 = null;
		V granted = null;
		int n1Dir = 0;
		for (int i = 0; i < 8; i++) {
			if ((mask & j) != 0) {
				Node node = getEntry(key | i);
				// mask存在即确定会有entry在
				// noinspection all
				if (node.v instanceof ArrayList) {
					// 下面没了
					ArrayList<V> list = (ArrayList<V>) node.v;
					for (int k = 0; k < list.size(); k++) {
						V v = list.get(k);
						if (v.getPos().lengthSquared() < n1Dist) {
							n1Dist = (int) v.getPos().lengthSquared();
							n1 = node;
							granted = v;
							n1Dir = j;
							listId = k;
						}
					}
				} else {
					V v = (V) node.v;
					if (v.getPos().lengthSquared() < n1Dist) {
						n1Dist = (int) v.getPos().lengthSquared();
						n1 = node;
						granted = v;
						n1Dir = j;
						listId = -1;
					}
				}
			}
			j <<= 1;
		}

		if (n1 == null) {
			// n没有child
			removeNode(n);
			return false;
		} else {
			if (n.v instanceof ArrayList) ((ArrayList<?>) n.v).remove(listId);

			n.v = granted; // 把最近的child拉上来
			// 重复此过程...
			if (!graft(n1)) {
				// 直到没有child，更新flag
				n.mask ^= n1Dir;
			}
			return true;
		}
	}

	private void removeNode(Node node) {
		// 非常不幸，我们不是双向链表
		int kid = (node.k ^ (node.k >>> 16)) & mask;
		Node first = nodes[kid];

		Node prev = null;
		while (first != node) {
			prev = first;
			first = first.next;
		}

		size--;

		if (prev != null) {
			prev.next = node.next;
		} else {
			nodes[kid] = node.next;
		}

		Node cache = this.cache;
		if (cache != null && cache.k > 16) return;
		node.next = cache;
		node.k = cache == null ? 1 : cache.k + 1;
		node.v = null;
		this.cache = node;
	}

	/**
	 * 获取邻近的一些元素 <br>
	 * 关于 {@link #rcGetOrigin} 的补充说明：
	 * 根据此Octree的设计，任意节点的坐标都比其孩子节点的更靠近原点 <br>
	 * 关于 {@link LocationSet#setLimit} 的补充说明：
	 * 你甚至不会获取到距离原点最近的limit个元素，因为我们是深度优先的递归 <br>
	 */
	public final LocationSet<V> getSome(Vec3i pos, float rMin, float rMax, LocationSet<V> rs) {
		boolean origin = pos.lengthSquared() == 0;

		int unit = this.unit;
		for (int i = 0; i < 8; i++) {
			Node n = getEntry(0b1000 | i);
			if (n != null) {
				rs.cx = ((i & 4) != 0 ? -unit : unit);
				rs.cy = ((i & 2) != 0 ? -unit : unit);
				rs.cz = ((i & 1) != 0 ? -unit : unit);
				if (overlaps(pos, rs, rMax, unit)) {
					if (origin) rcGetOrigin(n, rMin, rMax, rs, unit >>> 1);
					else if (!rs.nearest) rcGetAny(n, pos, rMin, rMax, rs, unit >>> 1);
					else rMax = rcGetNearest(n, pos, rMin, rMax, rs, unit >>> 1);
				}
			}
		}
		return rs;
	}

	private static final Vec3i ORIGIN = new Vec3i();

	static boolean overlaps(Vec3i pos, LocationSet<?> center, float r, int unit) {
		int dx = pos.x - center.cx;
		int dy = pos.y - center.cy;
		int dz = pos.z - center.cz;
		if (dx < 0) dx = -dx;
		if (dy < 0) dy = -dy;
		if (dz < 0) dz = -dz;

		// 太远了
		int max = (int) r + unit;
		if (dx > max || dy > max || dz > max) return false;

		// 碰到了正方体的面
		int ok = 0;
		if (dx < unit) ok++;
		if (dy < unit) ok++;
		if (dz < unit) ok++;
		if (ok > 1) return true;

		// 碰到了棱或顶点
		// since the case of the ball center inside octant has been considered,
		// we only consider the ball center outside octant
		int dx1 = Math.max(dx - unit, 0);
		int dy1 = Math.max(dy - unit, 0);
		int dz1 = Math.max(dz - unit, 0);

		return dx1 * dx1 + dy1 * dy1 + dz1 * dz1 < r * r;
	}

	/**
	 * 获取离pos一定范围的元素
	 *
	 * @param unit this.unit >>> n所处的层级
	 */
	@SuppressWarnings("unchecked")
	public final void rcGetAny(Node n, Vec3i pos, float rMin, float rMax, LocationSet<V> rs, int unit) {
		float L2 = rMin * rMin, H2 = rMax * rMax;
		if (n.v.getClass() == ArrayList.class) {
			ArrayList<V> list = (ArrayList<V>) n.v;
			for (int i = 0; i < list.size(); i++) {
				int d2 = (int) list.get(i).getPos().distanceSq(pos);
				if (L2 <= d2 && d2 <= H2) {
					if (!rs.add(d2, list.get(i))) break;
				}
			}
			return;
		}

		V v = (V) n.v;
		int d2 = (int) v.getPos().distanceSq(pos);
		if (L2 <= d2 && d2 <= H2) {
			if (!rs.add(d2, v)) return;
		}

		int k = n.k << 3;
		int mask = n.mask & 0xFF;
		int shl = 1;
		int cx = rs.cx, cy = rs.cy, cz = rs.cz;
		for (int i = 0; i < 8; i++) {
			if ((mask & shl) != 0) {
				rs.cx = cx + ((i & 4) != 0 ? -unit : unit);
				rs.cy = cy + ((i & 2) != 0 ? -unit : unit);
				rs.cz = cz + ((i & 1) != 0 ? -unit : unit);

				// point in box
				if (overlaps(pos, rs, rMax, unit)) rcGetAny(getEntry(k | i), pos, rMin, rMax, rs, unit >>> 1);
			}
			shl <<= 1;
		}
	}

	/**
	 * 获取离原点一定范围的元素
	 *
	 * @param unit this.unit >>> n所处的层级
	 */
	@SuppressWarnings("unchecked")
	public final void rcGetOrigin(Node n, float rMin, float rMax, LocationSet<V> rs, int unit) {
		float L2 = rMin * rMin, H2 = rMax * rMax;
		if (n.v.getClass() == ArrayList.class) {
			ArrayList<V> list = (ArrayList<V>) n.v;
			for (int i = 0; i < list.size(); i++) {
				int d2 = (int) list.get(i).getPos().lengthSquared();
				if (L2 <= d2 && d2 <= H2) {
					if (!rs.add(d2, list.get(i))) break;
				}
			}
			return;
		}

		V v = (V) n.v;
		int d2 = (int) v.getPos().lengthSquared();
		if (d2 > H2) return;
		if (L2 <= d2) if (!rs.add(d2, v)) return;

		int k = n.k << 3;
		int mask = n.mask & 0xFF;
		int shl = 1;
		int cx = rs.cx, cy = rs.cy, cz = rs.cz;
		for (int i = 0; i < 8; i++) {
			if ((mask & shl) != 0) {
				rs.cx = cx + ((i & 4) != 0 ? -unit : unit);
				rs.cy = cy + ((i & 2) != 0 ? -unit : unit);
				rs.cz = cz + ((i & 1) != 0 ? -unit : unit);

				// point in box
				if (overlaps(ORIGIN, rs, rMax, unit)) rcGetOrigin(getEntry(k | i), rMin, rMax, rs, unit >>> 1);
			}
			shl <<= 1;
		}
	}

	/**
	 * 获取离pos最近的元素
	 *
	 * @param unit this.unit >>> n所处的层级
	 */
	@SuppressWarnings("unchecked")
	public final float rcGetNearest(Node n, Vec3i pos, float rMin, float rMax, LocationSet<V> rs, int unit) {
		float L2 = rMin * rMin, H2 = rMax * rMax;
		if (n.v.getClass() == ArrayList.class) {
			ArrayList<V> list = (ArrayList<V>) n.v;
			for (int i = 0; i < list.size(); i++) {
				int d2 = (int) list.get(i).getPos().distanceSq(pos);
				if (d2 <= H2) {
					if (L2 <= d2) rs.add(d2, list.get(i));
					H2 = d2;
				}
			}
			return (float) Math.sqrt(H2);
		}

		V v = (V) n.v;
		int d2 = (int) v.getPos().distanceSq(pos);
		if (d2 <= H2) {
			if (L2 <= d2) rs.add(d2, v);
			rMax = (float) Math.sqrt(d2);
		}

		int k = n.k << 3;
		int mask = n.mask & 0xFF;
		int shl = 1;
		int cx = rs.cx, cy = rs.cy, cz = rs.cz;
		for (int i = 0; i < 8; i++) {
			if ((mask & shl) != 0) {
				rs.cx = cx + ((i & 4) != 0 ? -unit : unit);
				rs.cy = cy + ((i & 2) != 0 ? -unit : unit);
				rs.cz = cz + ((i & 1) != 0 ? -unit : unit);

				// point in box
				if (overlaps(pos, rs, rMax, unit)) rMax = rcGetNearest(getEntry(k | i), pos, rMin, rMax, rs, unit >>> 1);
			}
			shl <<= 1;
		}

		return rMax;
	}

	public Node parent(Node node) {
		return getEntry(node.k >>> 3);
	}

	public Node child(Node node, int dir) {
		// 没必要判断dir和mask，反正不存在也是null
		return getEntry((node.k << 3) | dir);
	}

	private void resize() {
		Node[] nodes1 = new Node[mask + 1];
		int i = 0, j = nodes.length;
		for (; i < j; i++) {
			Node entry = nodes[i];
			while (entry != null) {
				Node next = entry.next;
				int slot = entry.k;
				slot = (slot ^ (slot >>> 16)) & mask;
				Node orig = nodes1[slot];
				nodes1[slot] = entry;
				entry.next = orig;
				entry = next;
			}
		}

		this.nodes = nodes1;
	}

	Node cache;
	Node[] nodes;
	int size, mask;

	public final Node getEntry(int id) {
		Node entry = first(id, false);
		while (entry != null) {
			if (entry.k == id) return entry;
			entry = entry.next;
		}
		return null;
	}

	Node getOrCreateEntry(int id) {
		Node entry = first(id, true);
		while (true) {
			if (entry.k == id) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}

		return entry.next = getCache(id);
	}

	private Node getCache(int id) {
		Node cache = this.cache;
		if (cache != null) {
			cache.k = id;
			cache.v = null;
			this.cache = cache.next;
			cache.next = null;
			return cache;
		}

		return new Node(id);
	}

	private Node first(int k, boolean create) {
		int id = (k ^ (k >>> 16)) & mask;
		if (nodes == null) {
			if (!create) return null;
			nodes = new Node[mask + 1];
		}
		Node node;
		if ((node = nodes[id]) == null) {
			if (!create) return null;
			return nodes[id] = getCache(k);
		}
		return node;
	}

	/**
	 * 补充说明：此迭代器无任何显式的顺序
	 */
	@NotNull
	public Iterator<Node> iterator() { return new _Generic_EntryItr<>(this); }
	public _Generic_Entry[] __entries() { return nodes; }
	public void __remove(Node node) { throw new UnsupportedOperationException(); }

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Octree{unit=").append(unit).append(", val=");
		if (size > 0) {
			for (Node entry : this) {
				sb.append(entry.k).append(": ").append(entry.v).append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
		}
		return sb.append('}').toString();
	}

}