package roj.compiler.runtime;

import roj.util.Helpers;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2024/6/9 0009 21:26
 */
public abstract class Generator<T> implements Iterator<T> {
	private byte stage;
	// position, returnValue, registers
	private final ReturnStack<?> stack = new ReturnStack<>();

	protected Generator() {stack.forWrite().put(0);}

	@Override
	public final boolean hasNext() {
		if (stage == 0) {
			try {
				if (u.getInt(stack.address()) == -1) return false;
				invoke();
				stage = 1;
			} catch (Throwable e) {
				u.putInt(stack.address(), -1);
				Helpers.athrow(e);
			}
		}

		return stage == 1;
	}

	private void check() {
		if (!hasNext()) throw new NoSuchElementException();
		stage = 0;
	}

	@Override
	@SuppressWarnings("unchecked")
	public final T next() {check();return (T) stack.forRead().getL();}
	public final int nextInt() {check();return u.getInt(stack.address()+4);}
	public final long nextLong() {check();return u.getLong(stack.address()+4);}
	public final float nextFloat() { return Float.floatToIntBits(nextInt()); }
	public final double nextDouble() { return Double.longBitsToDouble(nextLong()); }

	protected final ReturnStack<?> __pos() {return stack.forRead();}
	protected final ReturnStack<?> __yield(int position) {return stack.forWrite().put(position);}
	protected abstract void invoke() throws Throwable;
}