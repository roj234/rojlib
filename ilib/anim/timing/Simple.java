package ilib.anim.timing;

import roj.text.StringPool;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2021/5/27 22:52
 */
abstract class Simple extends Timing implements Timing.Factory {
	@Override
	public Factory factory() {
		return this;
	}

	@Override
	public Timing createNew() {
		return this;
	}

	@Override
	public Timing readFrom(ByteList r, StringPool pool) {
		return this;
	}
}
