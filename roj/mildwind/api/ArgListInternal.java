package roj.mildwind.api;

import roj.collect.SimpleList;
import roj.mildwind.JsContext;
import roj.mildwind.type.JsObject;

/**
 * @author Roj234
 * @since 2020/10/27 22:44
 */
public final class ArgListInternal extends Arguments {
	final JsContext vm;
	int refCount;

	public ArgListInternal(JsContext vm, int size) {
		super(new SimpleList<>(size));
		this.vm = vm;
		this.refCount = 1;
	}

	public ArgListInternal push(JsObject obj) {
		argv.add(obj);
		obj._ref();
		return this;
	}
	public ArgListInternal pushAll(JsObject arrayLike) {
		int length = arrayLike.length().asInt();
		for (int i = 0; i < length; i++) {
			JsObject ref = arrayLike.getByInt(i);
			ref._ref();
			push(ref);
		}
		return this;
	}

	@Override
	public void _ref() {
		// TODO: reference counter
	}

	@Override
	public void _unref() {

	}

	@Override
	public void dispose() {
		for (JsObject object : argv) object._unref();
		vm.add(this);
	}
}
