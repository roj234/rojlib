package roj.config.node;

import org.jetbrains.annotations.NotNull;
import roj.config.ValueEmitter;
import roj.text.DateFormat;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class DateValue extends LongValue {
	public DateValue(long value) {super(value);}

	@Deprecated
	public static DateValue valueOf(String val) {return new DateValue(DateFormat.parseISO8601Datetime(val));}

	@NotNull
	@Override public Type getType() { return Type.DATE; }
	@Override public boolean mayCastTo(Type o) { return o == Type.DATE; }
	@Override public void accept(ValueEmitter visitor) { visitor.emitDate(value); }

	@Override
	public String toString() {return DateFormat.toISO8601Datetime(value);}
}