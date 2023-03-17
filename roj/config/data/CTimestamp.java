package roj.config.data;

import roj.config.serial.CConsumer;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class CTimestamp extends CLong {
	public CTimestamp(long number) {
		super(number);
	}

	@Nonnull
	@Override
	public Type getType() {
		return Type.DATE;
	}

	@Override
	public void forEachChild(CConsumer ser) {
		ser.valueTimestamp(value);
	}
}
