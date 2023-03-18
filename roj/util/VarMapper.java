package roj.util;

import roj.collect.*;
import roj.collect.RSegmentTree.Point;
import roj.collect.RSegmentTree.Range;
import roj.collect.RSegmentTree.Region;
import roj.text.CharList;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 根据变量作用域分配可重用的ID ...... <BR>
 * 同时根据set/get拆分细化变量作用域 <BR>
 * 进而进一步降低所需的ID数目 <BR>
 *
 * @author Roj233
 * @since 2022/2/23 15:27
 */
public class VarMapper {
	final RSegmentTree<Jump> jumps = new RSegmentTree<>(10, true, 50);
	final RSegmentTree<Var> vars = new RSegmentTree<>(10, false, 100);

	private final MyBitSet tmp1 = new MyBitSet();
	private final List<Var> tmp2 = new SimpleList<>();

	public RSegmentTree<Var> getVars() { return vars; }

	public void add(Var v) { vars.add(v); }
	public int map() {
		RSegmentTree<Var> union = vars;

		MyBitSet freeId = tmp1;
		List<Var> curVars = tmp2;

		int id = 0, peakId = 0;

		Region[] list = union.dataArray();
		for (int j = 0; j < union.arraySize(); j++) {
			Point p = list[j].node();
			while (p != null) {
				Var v = p.owner();
				if (p.end()) {
					if (v.slot == id) id--;
					else freeId.add(v.slot);
				} else {
					curVars.add(p.owner());
				}
				p = p.next();
			}

			for (int i = 0; i < curVars.size(); i++) {
				Var v = curVars.get(i);
				int last = freeId.first();
				if (last < 0) {
					v.slot = id++;
				} else {
					freeId.remove(last);
					v.slot = last;
				}

				if (id > peakId) peakId = id;
			}
			curVars.clear();
		}
		freeId.clear();

		return peakId;
	}

	public void jump(int from, int to) { jumps.add(new Jump(from, to)); }
	public int mapEx(Collection<? extends VarX> tmp) {
		RSegmentTree<Var> vars = this.vars;
		vars.clear();

		RSegmentTree<Jump> reverseJump = jumps;

		// A. 当一变量assign后,此语句下方没有语句能返回上方,则可将其视为新变量
		// 并尝试给一个较小的ID
		for (VarX x : tmp) {
			// 只assign了一次或零次(undefined)
			if (x.S.size() < 2) {
				// get过
				if (x.start < x.end) {
					vars.add(x);
					x.subVars.add(x);
				}
				continue;
			}

			// 拆分
			int lastPos = x.start;
			// 起始Region id
			int rStr = Math.max(0, reverseJump.search(lastPos));
			// set计数
			int k = 0;
			// 上一个变量
			Var v = null;

			Region[] array = reverseJump.dataArray();

			IntIterator itr = x.S.iterator();
			boolean b = x.start == itr.nextInt();
			assert b;

			boolean next = true;
			while (next) {
				k++;
				int pos;
				if (itr.hasNext()) {
					pos = itr.nextInt();
				} else {
					pos = x.end;
					next = false;
				}

				// +1: assign"后"
				int rEnd = reverseJump.search(pos + 1);
				if (rEnd < 0) continue;

				// B. 若一变量在两次assign间没有get, 当然,它们之间也没有跳转
				// 则前一次assign可以放弃 (当然要保留side-effect)
				checkAssign:
				if (k < x.G.length && x.G[k] == -1) {
					for (; rStr < rEnd; rStr++) {
						if (!array[rStr].value().isEmpty()) {
							break checkAssign;
						}
					}

					if (v != null) {
						rStr = rEnd;
						lastPos = pos;
						continue;
					} else {
						System.out.println("v=null && allEmpty");
					}
				}

				rStr = rEnd;
				if (!array[rEnd].value().isEmpty()) {
					if (next) continue;

					// todo 依然有反向跳转且 [待续]
					System.out.println("? backward jmp and not implemented " + array[rEnd].value());
				}

				// 如果都能返回上方
				if (lastPos == x.start && pos == x.end) {
					vars.add(x);
					x.subVars.add(x);
				} else {
					// 或者这就是新变量
					v = new Var();
					v.start = lastPos;
					v.end = lastPos = x.G[k] < 0 ? pos : x.G[k];

					vars.add(v);
					x.subVars.add(v);
				}
			}
		}

		return map();
	}

	public final IntMap<VarX> vars__ = new IntMap<>();
	public VarX addIfAbsentEx(int id) {
		VarX x = vars__.get(id);
		if (x == null) {
			vars__.put(id, x = new VarX());
			x.att = id;
		}
		return x;
	}

	public void clear() {
		vars__.clear();
		vars.clear();
		jumps.clear();
	}

	public final void dump(List<? extends VarX> list, int width) {
		MyBitSet free = tmp1;
		free.clear();
		RSegmentTree<Var> union = this.vars;
		union.clear();
		union.withContent(true);

		for (int i = 0; i < list.size(); i++) {
			union.add(list.get(i));
		}

		PrintStream pr = System.out;
		CharList sb = new CharList();

		int height = 0;
		for (int i = 0; i < list.size(); i++) {
			height = Math.max((int) list.get(i).endPos(), height);
		}
		int pad = Integer.toString(height).length();

		for (int i = 0; i < height; i++) {
			List<Var> v1 = union.collect(i);

			free.clear();
			for (int j = 0; j < v1.size(); j++) {
				free.add(v1.get(j).slot);
			}

			String s = Integer.toString(i);
			sb.append(s);
			for (int k = pad - s.length(); k >= 0; k--) {
				sb.append(' ');
			}

			for (int j = 0; j < width; j++) {
				sb.append(free.contains(j) ? '*' : ' ');
			}
			pr.println(sb);
			sb.clear();
		}

		union.clear();
		union.withContent(false);
	}

	public static class Var implements Range {
		public int start, end, slot;
		public Object att;

		public long startPos() { return start; }
		public long endPos() { return end; }

		@Override
		public String toString() {
			return "Var{" + "[" + start + "," + end + "]@" + slot + '}';
		}
	}
	public static class VarX extends Var {
		{ start = -1; }

		final MyBitSet S = new MyBitSet();
		int[] G = {-1, -1};

		public final List<Var> subVars = new SimpleList<>();

		public void reset() {
			for (int i = 0; i < S.size(); i++) G[i] = -1;
			S.clear();
			subVars.clear();
		}

		public final void set(int pos) {
			if (start < 0) start = pos;
			end = pos;

			S.add(pos);
		}

		public final void get(int pos) {
			if (start < 0) start = pos;
			end = pos;

			MyBitSet S = this.S;
			if (G.length <= S.size()) {
				int[] G = Arrays.copyOf(this.G, S.size() + 1);
				for (int i = this.G.length; i <= S.size(); i++) {
					G[i] = -1;
				}
				this.G = G;
			}
			G[S.size()] = pos;
		}

		public final void getset(int pos) {
			get(pos);
			S.add(pos);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder().append(super.toString()).append(", subVars=[");
			for (int i = 0; i < subVars.size(); i++) {
				sb.append(subVars.get(i) == this ? "<recursion>" : subVars.get(i)).append(',');
			}
			return sb.append("]}").toString();
		}
	}

	static final class Jump implements Range {
		int from, to;

		Jump(int f, int t) {
			if (f > t) {
				from = t;
				to = f;
			} else {
				from = f;
				to = t;
			}
		}

		public long startPos() { return from; }
		public long endPos() { return to; }

		@Override
		public String toString() {
			return "Jump{" + from + "=>" + to + '}';
		}
	}
}
