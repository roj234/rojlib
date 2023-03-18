package roj.config.data;

import roj.text.CharList;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/12 13:21
 */
public final class CTOMLList extends CList {
	public boolean fixed = true;

	public CTOMLList() {}
	public CTOMLList(int size) { super(size); }
	public CTOMLList(List<CEntry> list) { super(list); }

	public CharList toTOML(CharList sb, int depth, CharSequence chain) { return super.toTOML(sb, fixed ? 3 : depth, chain); }
}
