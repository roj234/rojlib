package roj.config.data;

import org.jetbrains.annotations.NotNull;
import roj.config.serial.CVisitor;
import roj.text.DateParser;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class CDate extends CLong {
	public CDate(long value) {super(value);}

	@Deprecated
	public static CDate valueOf(String val) {return new CDate(DateParser.parseISO8601Datetime(val));}

	@NotNull
	@Override public Type getType() { return Type.DATE; }
	@Override public boolean mayCastTo(Type o) { return o == Type.DATE; }
	@Override public void accept(CVisitor ser) { ser.valueDate(value); }
}