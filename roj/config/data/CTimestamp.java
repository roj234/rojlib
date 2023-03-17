package roj.config.data;

import roj.config.serial.CVisitor;

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
	public void forEachChild(CVisitor ser) {
		ser.valueTimestamp(value);
	}
}
