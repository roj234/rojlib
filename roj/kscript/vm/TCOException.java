package roj.kscript.vm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.func.KFunction;

/**
 * Tail Call Optimization
 *
 * @author solo6975
 * @since 2021/6/17 0:59
 */
public final class TCOException extends RuntimeException {
	public static final TCOException TCO_RESET = new TCOException();

	TCOException() {
		super("", null, false, false);
	}

	@Override
	public String toString() {
		return "Should be caught";
	}

	public TCOException reset(IObject $this, ArgList argList, KFunction fn, byte flag) {
		this.$this = $this;
		this.argList = argList;
		this.fn = fn;
		this.flag = flag;
		return this;
	}

	public IObject $this;
	public ArgList argList;
	public KFunction fn;
	public byte flag;
}
