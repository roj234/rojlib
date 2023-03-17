package roj.sa.yield;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static roj.collect.AbstractIterator.*;

/**
 * @author Roj234
 * @since 2023/4/20 0020 16:40
 */
public abstract class Generator<T> implements Iterator<T> {
	public byte stage = INITIAL;
	public int yield_pos;

	protected Generator() {}

	public void __yield_exit() {

	}

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

	public final float nextFloat() {
		return Float.floatToIntBits(nextInt());
	}

	public final double nextDouble() {
		return Double.longBitsToDouble(nextLong());
	}

	private void check() {
		if (stage <= 1) {
			if (!computeNext()) {
				stage = ENDED;
			} else {
				stage = CHECKED;
			}
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

	protected int I() { noimpl(); return 0; }
	protected long L() { noimpl(); return 0; }
	protected Object A() { noimpl(); return null; }
	protected abstract void invoke() throws Throwable;

	private void noimpl() {
		throw new UnsupportedOperationException(getClass().getName().concat(" not this type"));
	}
}
