package roj.mildwind.util;

import roj.collect.MyHashMap;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.JsSymbol;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/6/21 0021 2:31
 */
public final class ObjectShape implements Consumer<JsObject> {
	private final JsObject[] prototypes;
	private final MyHashMap<Object, JsObject> nameToOwner = new MyHashMap<>();
	private boolean valid;

	public ObjectShape(List<JsObject> ps) {
		this.prototypes = ps.toArray(new JsObject[ps.size()]);
	}

	public JsObject getOwner(String name) {
		if (!valid) construct();
		return nameToOwner.get(name);
	}

	private void construct() {
		// 从最低层开始直到Object
		for (JsObject obj : prototypes) {
			Iterator<JsObject> itr = obj._keyItr();
			while (itr.hasNext()) {
				JsObject key = itr.next();
				nameToOwner.putIfAbsent(key instanceof JsSymbol ? key : key.toString(), obj);
			}
			obj.addChangeListener(this);
		}

		valid = true;
	}

	public void accept(JsObject proto) { valid = false; }
}
