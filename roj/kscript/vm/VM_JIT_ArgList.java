package roj.kscript.vm;

import roj.kscript.api.ArgList;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * JIT Argument list
 *
 * @author Roj234
 * @since 2021/6/27 14:38
 */
final class VM_JIT_ArgList extends ArgList {
	public KFunction caller;

	public VM_JIT_ArgList() {}

	public VM_JIT_ArgList reset(KFunction fn, List<KType> argv) {
		this.caller = fn;
		this.argv = argv == null ? Collections.emptyList() : argv;
		return this;
	}

	@Override
	@Nullable
	public KFunction caller() {
		return caller;
	}

	@Override
	public void trace(List<StackTraceElement> collector) {
		throw new UnsupportedOperationException();
	}
}
