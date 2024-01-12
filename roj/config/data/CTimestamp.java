package roj.config.data;

import org.jetbrains.annotations.NotNull;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class CTimestamp extends CLong {
	public CTimestamp(long number) {
		super(number);
	}

	@NotNull
	@Override
	public Type getType() {
		return Type.DATE;
	}

	@Override
	public void forEachChild(CVisitor ser) {
		ser.valueTimestamp(value);
	}
}