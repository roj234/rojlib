package roj.mildwind.api;

import roj.mildwind.JsContext;
import roj.mildwind.type.*;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2020/10/27 22:44
 */
public class Arguments implements JsObject {
	public static final Arguments EMPTY = new Arguments();

	JsFunction caller;
	Arguments prev;
	List<JsObject> argv;

	/**
	 * no arg
	 */
	public Arguments() { this(Collections.emptyList(), null, null, 2); }
	public Arguments(List<JsObject> argv) { this(argv, null, null, 2); }

	public Arguments(List<JsObject> argv, Arguments prev, JsFunction caller) { this(argv, prev, caller, 2); }
	Arguments(List<JsObject> argv, Arguments prev, JsFunction caller, int strip) {
		this.caller = caller;
		this.argv = argv;
		this.prev = prev;
	}

	@Nullable
	public JsFunction caller() { return caller; }

	public void dispose() {}

	@Deprecated
	public void trace(List<StackTraceElement> collector) {
		String name = caller.get("name").toString();
		collector.add(new StackTraceElement("", name, "", 114));
		if (prev != null) prev.trace(collector);
	}

	// array-like
	public Type type() { return Type.OBJECT; }

	public JsObject getByInt(int id) {
		if (id >= argv.size()) return JsNull.UNDEFINED;
		JsObject obj = argv.get(id);
		obj._ref();
		return obj;
	}
	public void putByInt(int i, JsObject value) {
		if (i < 0 || i >= argv.size()) return;
		argv.set(i, value);
	}
	public JsObject getOrDefault(int id, JsObject def) { return id >= argv.size() ? def : argv.get(id); }
	public JsArray getAfter(int id) { return id >= argv.size() ? new JsArray() : new JsArray(argv.subList(id, argv.size())); }

	public JsObject length() { return JsContext.getInt(argv.size()); }

	public StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append("[arguments: ").append(argv.size()).append("]");
	}
}
