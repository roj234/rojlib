package roj.kscript.vm;

import roj.kscript.api.ArgList;
import roj.kscript.asm.Frame;
import roj.kscript.asm.Node;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/21 22:37
 */
final class VM_ArgList extends ArgList {
	public Frame caller;
	public Node from;

	public VM_ArgList() {}

	public VM_ArgList reset(Node from, Frame frame, List<KType> argv) {
		this.from = from;
		this.caller = frame;
		this.argv = argv == null ? Collections.emptyList() : argv;
		return this;
	}

	@Override
	@Nullable
	public KFunction caller() {
		return caller.owner();
	}

	@Override
	public void trace(List<StackTraceElement> collector) {
		caller.trace(from, collector);
	}
}
