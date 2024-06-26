package roj.config.data;

import roj.collect.MyHashMap;
import roj.config.serial.CVisitor;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/1/14 20:07
 */
public class CCommMap extends CMap {
	Map<String, String> comments = new MyHashMap<>();

	public CCommMap() {}
	public CCommMap(Map<String, CEntry> map) { super(map); }
	public CCommMap(Map<String, CEntry> map, Map<String, String> comment) {
		super(map);
		comments = comment;
	}

	@Override
	public void accept(CVisitor ser) {
		ser.valueMap();
		if (!map.isEmpty()) {
			for (Map.Entry<String, CEntry> entry : map.entrySet()) {
				String s = comments.get(entry.getKey());
				if (s != null) ser.comment(s);

				ser.key(entry.getKey());
				entry.getValue().accept(ser);
			}
		}
		ser.pop();
	}

	public void acceptOrdered(CVisitor ser, String... names) {
		ser.valueMap();
		for (String k : names) {
			CEntry v = map.get(k);
			if (v != null) {
				String s = comments.get(k);
				if (s != null) ser.comment(s);
				ser.key(k);
				v.accept(ser);
			}
		}
		ser.pop();
	}

	@Override
	public final String getComment(String key) { return comments.get(key); }
	public final CMap withComments() { return this; }
	public final boolean isCommentSupported() { return true; }
	public void putComment(String key, String val) { comments.put(key, val); }
	public void clearComments() {comments.clear();}
}