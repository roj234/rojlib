package roj.mildwind.type;

import roj.mildwind.JsContext;

/**
 * @author Roj234
 * @since 2023/6/20 0020 19:35
 */
public abstract class JsReferenceCounted implements JsObject {
	final JsContext vm;
	int refCount;

	JsReferenceCounted(JsContext vm) { this.vm = vm == null || !vm.compiling ? vm : null; }

	public final void put(String name, JsObject value) { _unref(); }
	public final boolean del(String name) { _unref(); return true; }

	public final JsObject getByInt(int i) { return get(Integer.toString(i)); }
	public final void putByInt(int i, JsObject value) {}
	public final boolean delByInt(int i) { _unref(); return true; }

	public final void _ref() { if (vm != null && refCount++ == 0) throw new IllegalStateException(); }
	public void _unref() { if (--refCount == 0 && vm != null) add(vm); }

	abstract void add(JsContext vm);
}
