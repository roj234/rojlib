package roj.reflect;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.type.Type;
import roj.io.IOUtil;
import roj.util.VarMapper;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static roj.collect.AbstractIterator.*;

/**
 * @author Roj234
 * @since 2023/4/20 0020 16:40
 */
public abstract class Generator<T> implements Iterator<T> {
	public static void main(String[] args) throws Exception {
		ConstantData data = Parser.parseConstants(IOUtil.readRes("roj/sa/yield/YieldTest.class"));
		MethodNode mn = data.methods.get(data.getMethod("looper"));

		ConstantData register = createRegister(data, mn);

		data.dump();
		register.dump();

		ClassDefiner.INSTANCE.defineClass(register);
		ClassDefiner.INSTANCE.defineClass(data);

		runTest();
	}

	private static void runTest() {
		Generator<String> looper = YieldTest.looper(0, 0, "");
		int j = 0;
		while (looper.hasNext()) {
			String value = looper.next();
			System.out.println(value);
			if (j++ == 42) break;
		}
	}

	public static ConstantData createRegister(ConstantData data, MethodNode method) {
		// inherit, create register class with all fields used
		// eg: int slot0
		// todo / FRAMEVISITOR
		return null;
	}


	private static int findField(ConstantData reg, VarMapper.Var var, Type type1) {
		// FIRST
		if (var instanceof VarMapper.VarX) {
			int field = reg.getField("p"+(var.slot-1));
			if (field >= 0) return field;
		}

		int field = reg.getField("v"+var.hashCode());
		if (field >= 0) return field;

		return reg.newField(0, "v"+var.hashCode(), type1.toDesc());
	}

	public byte stage = INITIAL;
	public int entry_id;

	protected Generator() {}

	@Override
	public final boolean hasNext() {
		check();
		return stage != ENDED;
	}

	@Override
	@SuppressWarnings("unchecked")
	public final T next() {
		check();
		if (stage == ENDED) throw new NoSuchElementException();
		stage = GOTTEN;
		return (T) A();
	}

	public final int nextInt() {
		check();
		if (stage == ENDED) throw new NoSuchElementException();
		stage = GOTTEN;
		return I();
	}
	public final long nextLong() {
		check();
		if (stage == ENDED) throw new NoSuchElementException();
		stage = GOTTEN;
		return L();
	}
	public final float nextFloat() { return Float.floatToIntBits(nextInt()); }
	public final double nextDouble() { return Double.longBitsToDouble(nextLong()); }

	private void check() {
		if (stage <= 1) {
			stage = !computeNext() ? ENDED : CHECKED;
		}
	}

	private boolean computeNext() {
		try {
			invoke();
		} catch (Throwable e) {
			stage = ENDED;
			e.printStackTrace();
			//Helpers.athrow(e);
		}

		return stage != ENDED;
	}

	public abstract void reset();
	protected int I() { noimpl(); return 0; }
	protected long L() { noimpl(); return 0; }
	protected Object A() { noimpl(); return null; }
	protected abstract void invoke() throws Throwable;

	private void noimpl() {
		throw new UnsupportedOperationException(getClass().getName().concat(" not this type"));
	}
}