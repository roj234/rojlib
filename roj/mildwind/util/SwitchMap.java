package roj.mildwind.util;

import roj.collect.ToIntMap;
import roj.mildwind.type.JsObject;

/**
 * @author Roj234
 * @since 2021/4/28 18:47
 */
public class SwitchMap extends ToIntMap<JsObject> {
	public SwitchMap() { super(8); }
	public SwitchMap(int cap) { super(cap); }

	@Override
	protected boolean equals(JsObject in, JsObject entry) {
		return in.op_feq(entry);
	}
}
