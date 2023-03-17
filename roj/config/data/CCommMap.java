package roj.config.data;

import roj.collect.MyHashMap;
import roj.config.serial.CVisitor;
import roj.text.CharList;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/1/14 20:07
 */
public class CCommMap extends CMapping {
	Map<String, String> comments = new MyHashMap<>();

	public CCommMap() {}
	public CCommMap(Map<String, CEntry> map) { super(map); }
	public CCommMap(int size) { super(size); }
	public CCommMap(Map<String, CEntry> map, Map<String, String> comment) {
		super(map);
		comments = comment;
	}

	@Override
	public final String getCommentInternal(String key) { return comments.get(key); }
	public final CMapping withComments() { return this; }
	public final boolean isCommentSupported() { return true; }

	public void putCommentDotted(String key, String val) {
		if (dot != null) {
			comm1(key, val);
		} else {
			comments.put(key, val);
		}
	}

	public void clearComments(boolean withSub) {
		if (withSub) super.clearComments(true);
		comments.clear();
	}

	public void comm1(String keys, String val) {
		CharList tmp = dot;
		tmp.clear();

		CEntry entry = this;
		int i = 0;
		do {
			i = _name(keys, tmp, i);

			CMapping map1 = entry.asMap();
			if (i == keys.length()) {
				map1.getComments().put(tmp.toString(), val);
				return;
			} else {
				entry = map1.map.getOrDefault(tmp, CNull.NULL);
				if (entry.getType() == Type.NULL) {
					map1.map.put(tmp.toString(), entry = new CCommMap());
				} else if (!entry.asMap().isCommentSupported()) {
					map1.map.put(tmp.toString(), entry = entry.asMap().withComments());
				}
			}
			tmp.clear();
		} while (i < keys.length());
		comments.put(keys, val);
	}

	@Deprecated
	private static int _name(String keys, CharList tmp, int i) {
		while (i < keys.length()) {
			char c = keys.charAt(i++);
			if (c == '.') {
				int l = tmp.length();
				if (l == 0 || tmp.list[l - 1] != '\\') {
					break;
				} else {
					tmp.setLength(l - 1);
				}
			}
			tmp.append(c);
		}
		return i;
	}

	@Override
	public final Map<String, String> getComments() {
		return comments;
	}

	@Override
	public void forEachChild(CVisitor ser) {
		ser.valueMap();
		if (!map.isEmpty()) {
			for (Map.Entry<String, CEntry> entry : map.entrySet()) {
				String s = comments.get(entry.getKey());
				if (s != null) ser.comment(s);

				ser.key(entry.getKey());
				entry.getValue().forEachChild(ser);
			}
		}
		ser.pop();
	}

	public void forEachChildSorted(CVisitor ser, String... names) {
		ser.valueMap();
		for (String k : names) {
			CEntry v = map.get(k);
			if (v != null) {
				String s = comments.get(k);
				if (s != null) ser.comment(s);
				ser.key(k);
				v.forEachChild(ser);
			}
		}
		ser.pop();
	}
}
