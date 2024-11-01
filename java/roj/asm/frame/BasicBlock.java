package roj.asm.frame;

import roj.collect.MyBitSet;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.Liu;
import roj.util.ArrayUtil;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:55
 */
public class BasicBlock {
	public int bci;
	private String desc;
	private final MyHashSet<BasicBlock> successor = new MyHashSet<>();
	public boolean noFrame;

	Var2[]
		inheritStack = new Var2[8],
		stack = new Var2[8],
		inheritLocal = new Var2[8],
		local = new Var2[8];
	int inheritStackSize, stackSize, inheritLocalMax, localMax;

	Var2[] vLocal, vStack;
	int vLocalSize, vStackSize;
	MyBitSet packed__localUsage = new MyBitSet();

	public BasicBlock(int bci, String desc) {
		this.bci = bci;
		this.desc = desc;
	}

	public void to(BasicBlock fs) {
		// add successor (previous block)
		successor.add(fs);
	}

	public void merge(String desc, boolean noFrame) {
		this.desc = this.desc + " | " + desc;
		if (!noFrame) this.noFrame = false;
	}

	public void chainUpdate(MyHashSet<BasicBlock> passed, SimpleList<BasicBlock> route, Var2[] initS) {
		if (route.contains(this)) return;
		route.add(this);

		for (BasicBlock fs : successor) {
			fs.chainUpdate(passed, route, initS);
			fs.comeFrom(route, initS);
		}

		route.remove(route.size()-1);
	}

	public void updatePreHook() {
		vLocal = Arrays.copyOf(local, Math.max(localMax, inheritLocalMax));
		vLocalSize = localMax;
		vStack = Arrays.copyOf(inheritStack, inheritStackSize);
		vStackSize = inheritStackSize;
	}

	boolean fff;

	private void comeFrom(SimpleList<BasicBlock> route, Var2[] initS) {
		int size = Math.max(localMax, initS.length);
		for (int i = 0; i < route.size(); i++) {
			BasicBlock p = route.get(i);
			if (p.localMax > size) size = p.localMax;
		}
		Var2[] vl = vLocal = new Var2[size];
		System.arraycopy(initS, 0, vl, 0, initS.length);
		for (int i = 0; i < route.size(); i++) {
			BasicBlock p = route.get(i);
			if (p != this) {
				for (int j = 0; j < p.inheritLocalMax; j++) {
					if (p.inheritLocal[j] != null)
						vl[j] = p.inheritLocal[j];
				}
			}
			for (int j = 0; j < p.localMax; j++) {
				if (p.local[j] != null)
					vl[j] = p.local[j];
			}
			if (p == this) {
				for (int j = 0; j < p.inheritLocalMax; j++) {
					if (p.inheritLocal[j] != null)
						vl[j] = p.inheritLocal[j];
				}
			}
		}
		for (int i = 0; i < localMax; i++) {
			Var2 o = local[i];
			Var2 var2 = vl[i];
			if (var2 == null) {
				if (o != null) vLocal[i] = o.copy();
			} else if (o != null) {
				try {
					var2.merge(o);
				} catch (IllegalStateException e) {
					vLocal[i] = o;
					System.out.println(inheritLocal[i] != null);
				}
			}
		}
	}

	public void updatePostHook() {

	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("#").append(bci);
		sb.append("\n    To: ").append(Liu.of(successor).map(x -> "#"+x.bci).toList());
		if (inheritLocalMax > 0) sb.append("\n    LR").append(ArrayUtil.toString(inheritLocal, 0, inheritLocalMax));
		if (localMax > 0) sb.append("\n    LW").append(ArrayUtil.toString(local, 0, localMax));
		if (inheritStackSize > 0) sb.append("\n    US").append(ArrayUtil.toString(inheritStack, 0, inheritStackSize));
		if (stackSize > 0) sb.append("\n     S").append(ArrayUtil.toString(stack, 0, stackSize));
		return sb.toString();
	}
}