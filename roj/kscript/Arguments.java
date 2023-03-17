package roj.kscript;

import roj.kscript.api.ArgList;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.reflect.TraceUtil;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/21 22:37
 */
public final class Arguments extends ArgList {
	private final KFunction caller;
	private final ArgList prev;
	private final StackTraceElement[] traces;

	/**
	 * no arg
	 */
	public Arguments() {
		this(Collections.emptyList(), null, null, 2);
	}

	/**
	 * no caller
	 */
	public Arguments(List<KType> argv) {
		this(argv, null, null, 2);
	}

	public Arguments(List<KType> argv, ArgList prev) {
		this(argv, prev, null, 2);
	}


	public Arguments(List<KType> argv, ArgList prev, KFunction caller) {
		this(argv, prev, caller, 2);
	}

	private Arguments(List<KType> argv, ArgList prev, KFunction caller, int strip) {
		this.caller = caller;
		argv.getClass();
		this.argv = argv;
		this.prev = prev;
		StackTraceElement[] traces = TraceUtil.getTraces(new Throwable());
		int i = prev == null ? traces.length : 0;
		if (prev != null) {
			for (StackTraceElement str : traces) {
				if (str.getClassName().equals("roj.kscript.ast.InvokeNode")) {
					break;
				}
				i++;
			}
		}
		this.traces = new StackTraceElement[i - strip];
		System.arraycopy(traces, strip, this.traces, 0, i - strip);
	}

	@Override
	@Nullable
	public KFunction caller() {
		return caller;
	}

	@Override
	public void trace(List<StackTraceElement> collector) {
		collector.addAll(Arrays.asList(traces));
		if (prev != null) prev.trace(collector);
	}
}
