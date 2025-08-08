package roj.config.data;

import org.jetbrains.annotations.NotNull;
import roj.config.serial.CVisitor;
import roj.text.DateFormat;
import roj.text.DateTime;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class CDate extends CLong {
	public CDate(long value) {super(value);}

	@Deprecated
	public static CDate valueOf(String val) {return new CDate(DateFormat.parseISO8601Datetime(val));}

	@NotNull
	@Override public Type getType() { return Type.DATE; }
	@Override public boolean mayCastTo(Type o) { return o == Type.DATE; }
	@Override public void accept(CVisitor visitor) { visitor.valueDate(value); }

	@Override
	public String toString() {return DateTime.GMT().toISOString(value);}
}