package roj.config.data;

import roj.collect.MyHashMap;
import roj.text.CharList;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/1/12 16:10
 */
public class CTOMLFxMap extends CMapping {
	public boolean fixed = true;

	public CTOMLFxMap() { super(); }
	public CTOMLFxMap(Map<String, CEntry> map) { super(map); }
	public CTOMLFxMap(int size) { super(new MyHashMap<>(size)); }

	public CharList toTOML(CharList sb, int depth, CharSequence chain) { return super.toTOML(sb, fixed ? 3 : depth, chain); }
}
