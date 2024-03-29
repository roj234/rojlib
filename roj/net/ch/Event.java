package roj.net.ch;

import roj.util.Identifier;

public class Event {
	public final Identifier id;

	public static final int RESULT_DEFAULT = 0, RESULT_ACCEPT = 1, RESULT_DENY = 2;
	private byte state;
	protected Object data;

	public Event(Identifier id) {
		this(id, null);
	}

	public Event(Identifier id, Object data) {
		this.id = id;
		this.data = data;
	}

	public int getResult() {
		return state >>> 1;
	}

	public void setResult(int result) {
		if (result < 0 || result > 2) throw new IllegalArgumentException();
		state = (byte) ((result << 1) | (state & 1));
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public boolean isCancelled() {
		return (state & 1) != 0;
	}

	public void setCancelled(boolean cancelled) {
		if (cancelled) state |= 1;
		else state &= ~1;
	}

	private static final byte STOP = 32, REVERSE = 64;
	public void stopPropagate() { state |= STOP; }
	public Event capture() { state |= REVERSE; return this; }

	public boolean _stop() { return (state&STOP) != 0; }
	public boolean _reverse() { return (state&REVERSE) != 0; }
}
