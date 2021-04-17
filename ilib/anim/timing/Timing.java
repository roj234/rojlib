package ilib.anim.timing;

import roj.text.StringPool;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2021/5/27 21:59
 */
public abstract class Timing {
	public abstract Factory factory();

	public Timing setConfig(int key, double value) {
		return this;
	}

	public abstract double interpolate(double percent);

	interface Factory {
		String name();

		Timing createNew();

		Timing readFrom(ByteList r, StringPool pool);
	}
}
