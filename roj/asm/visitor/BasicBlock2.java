package roj.asm.visitor;

import roj.asm.frame.Var2;
import roj.collect.MyBitSet;
import roj.collect.MyHashSet;
import roj.util.ArrayUtil;

/**
 * todo 重用此对象
 * 这一次放弃了三次重写都未达标的InsnNode/Recursion方法
 * @author Roj234
 * @since 2022/11/17 0017 12:55
 */
public class BasicBlock2 {
	public int bci;
	private final String desc;
	private final MyHashSet<BasicBlock2> successor = new MyHashSet<>();
	public boolean noFrame;

	Var2[]
		inheritStack = new Var2[8],
		stack = new Var2[8],
		inheritLocal = new Var2[8],
		local = new Var2[8];
	int inheritStackSize, stackSize, inheritLocalMax, localMax;

	Var2[] packed__local, packed__stack;
	MyBitSet packed__localUsage = new MyBitSet();

	public BasicBlock2(int bci, String desc) {
		this.bci = bci;
		this.desc = desc;
	}

	public void to(BasicBlock2 fs) {
		// add successor (previous block)
		successor.add(fs);
	}

	public void merge(BasicBlock2 fs) {
		// addAll successor (same bci)
		successor.addAll(fs.successor);
		if (fs.noFrame != noFrame) noFrame = false;
	}

	public void chainUpdate(MyHashSet<BasicBlock2> passed) {
		if (!passed.add(this)) return;
		for (int i = Math.min(inheritLocalMax, localMax) - 1; i >= 0; i--) {
			if (local[i] != null && local[i].eq(inheritLocal[i])) {
				local[i] = null;
			}
		}

		for (BasicBlock2 fs : successor) {
			fs.chainUpdate(passed);
			fs.comeFrom(this);
		}
	}

	public void updatePreHook() {
		packed__local = local.clone();
	}

	private void comeFrom(BasicBlock2 p) {
		System.out.println(p.bci + " comes to " + bci);
		//if (packed__local.length < p.lo)
		//if (p.stackSize < inheritStackSize) throw new RuntimeException("Stack underflow");
		for (int i = 0; i < inheritLocalMax; i++) {
			if (inheritLocal[i] != null) {
				p.packed__localUsage.add(i);
			}
		}
	}

	public void updatePostHook() {

	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append(desc).append(" at ").append(bci);
		if (inheritLocalMax > 0)
			sb.append("\n    LR").append(ArrayUtil.toString(inheritLocal, 0, inheritLocalMax));
		if (localMax > 0)
			sb.append("\n    LW").append(ArrayUtil.toString(local, 0, localMax));
		if (inheritStackSize > 0)
			sb.append("\n    US").append(ArrayUtil.toString(inheritStack, 0, inheritStackSize));
		if (stackSize > 0)
			sb.append("\n     S").append(ArrayUtil.toString(stack, 0, stackSize));
		return sb.toString();
	}
}
