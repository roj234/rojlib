package roj.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Tail Call Optimization
 *
 * @author solo6975
 * @since 2021/6/27 7:25
 */
public abstract class TCO<T, R> implements Function<T, R> {
	boolean zero;

	private Object[] stack = new Object[4];
	private int stackUsed;

	@NotNull
	@SuppressWarnings("unchecked")
	public final T pop() {
		T v = (T) stack[--stackUsed];
		stack[stackUsed] = null;
		return v;
	}

	public final void push(@NotNull T base) {
		if (stackUsed == stack.length) {
			Object[] plus = new Object[(int) (stackUsed * 1.5)];
			System.arraycopy(stack, 0, plus, 0, stackUsed);
			stack = plus;
		}

		stack[stackUsed] = base;

		if (++stackUsed > 2048) throw new IllegalStateException("Stack overflow(2048): " + this);
	}

	public final void stackClear() {
		for (int i = 0; i < stackUsed; i++) {
			stack[i] = null;
		}
		stackUsed = 0;
	}

	public R apply(T arg) {
		push(arg);
		if (!zero) {
			zero = true;
			R r = null;
			while (stackUsed > 0) {
				r = recursion(pop());
			}
			zero = false;
			return r;
		}

		return null;
	}

	/**
	 * 目标递归函数 <br>
	 * requirement: 将其中所有调用自身换为调用apply
	 */
	protected abstract R recursion(T t);
}