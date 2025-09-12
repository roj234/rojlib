package roj.config.node;

import roj.config.ValueEmitter;
import roj.text.DateFormat;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class TimestampValue extends LongValue {
	public TimestampValue(long number) { super(number); }

	public Type getType() { return Type.DATE; }
	@Override public boolean mayCastTo(Type o) { return o == Type.DATE; }
	@Override public void accept(ValueEmitter visitor) { visitor.emitTimestamp(value); }

	@Override
	public String toString() {return DateFormat.toISO8601Datetime(value);}
}