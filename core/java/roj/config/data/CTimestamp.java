package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.DateTime;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class CTimestamp extends CLong {
	public CTimestamp(long number) { super(number); }

	public Type getType() { return Type.DATE; }
	@Override public boolean mayCastTo(Type o) { return o == Type.DATE; }
	@Override public void accept(CVisitor visitor) { visitor.valueTimestamp(value); }

	@Override
	public String toString() {return DateTime.GMT().toISOString(value);}
}