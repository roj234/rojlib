package roj.compiler.runtime;

import roj.util.Helpers;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/6/9 21:26
 */
public abstract class Generator<T> implements Iterator<T> {
	private byte stage;
	// position, returnValue, registers
	protected final ReturnStack<?> stack = new ReturnStack<>();
	protected Generator() {stack.forWrite();}

	@Override
	public final boolean hasNext() {
		if (stage == 0) {
			try {
				invoke(stack.forRead());
				stage = (byte) (U.getInt(stack.base()) == -1 ? -1 : 1);
			} catch (Throwable e) {
				stage = -1;
				stack.forWrite();

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
	public final T next() {check();return (T) stack.objects.getLast();}
	public final int nextInt() {check();return U.getInt(stack.address-4);}
	public final long nextLong() {check();return U.getLong(stack.address-8);}
	public final float nextFloat() { return Float.floatToIntBits(nextInt()); }
	public final double nextDouble() { return Double.longBitsToDouble(nextLong()); }

	protected abstract void invoke(ReturnStack<?> stack) throws Throwable;
}