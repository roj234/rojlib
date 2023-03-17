package roj.mildwind.util;

import roj.mildwind.api.Arguments;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;

/**
 * Tail Call Optimization
 *
 * @author solo6975
 * @since 2021/6/17 0:59
 */
public final class TCOException extends RuntimeException {
	public static final TCOException TCO_RESET = new TCOException();

	public TCOException() { super("", null, false, false); }

	@Override
	public String toString() { return "Should be caught"; }

	public TCOException reset(JsObject $this, Arguments argList, JsFunction fn, byte flag) {
		this.self = $this;
		this.args = argList;
		this.fn = fn;
		this.flag = flag;
		return this;
	}

	public JsObject self;
	public Arguments args;
	public JsFunction fn;
	public byte flag;
}
