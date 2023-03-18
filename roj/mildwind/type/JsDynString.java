package roj.mildwind.type;

import roj.mildwind.JsContext;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public final class JsDynString extends JsString {
	public final CharList sb = new CharList();

	public JsDynString() { super(JsContext.context(), ""); value = null; }

	public void _unref() {
		if (value == null) {
			value = sb.toString();
			checkNum();
		}
		_myunref();
	}
	private void _myunref() {
		if (--refCount == 0) sb._free();
	}

	public JsObject op_add(JsObject r) {
		_myunref();
		sb.append(r instanceof JsDynString ? ((JsDynString) r).sb : r.toString());
		value = null;
		return this;
	}
	public boolean op_equ(JsObject o) { _myunref(); return (value==null?sb:value).equals(o.toString()); }
	public int op_leq(JsObject r) { _myunref(); return sb.compareTo(r.toString()); }
}
