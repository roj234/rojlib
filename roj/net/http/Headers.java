package roj.net.http;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.LineReader;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * @author solo6975
 * @since 2021/10/23 21:20
 */
public class Headers extends MyHashMap<CharSequence, String> {
	private static final class E extends MyHashMap.Entry<CharSequence, String> implements BiConsumer<String, String> {
		List<String> all;

		public E(CharSequence k) {
			super(k, null);
			this.all = Collections.emptyList();
		}

		// awa, 省一个类, 我真无聊
		public final void accept(String k, String v) {
			if (this.k.equals(k)) {
				this.k = v;
				throw OperationDone.INSTANCE;
			}
		}
	}

	public static String decodeOneValue(CharSequence field, String key) {
		E vo = new E(key);
		try {
			decodeVal(field, vo);
		} catch (OperationDone e) {
			return Helpers.cast(vo.k);
		}
		return null;
	}
	public static List<Map.Entry<String, String>> decodeToList(CharSequence field) {
		List<Map.Entry<String, String>> kvs = new SimpleList<>();
		decodeVal(field, (k, v) -> kvs.add(new SimpleImmutableEntry<>(k, v)));
		return kvs;
	}
	public static String findInValue(List<Map.Entry<String, String>> list, String name) {
		String s = null;
		for (int i = 0; i < list.size(); i++) {
			Map.Entry<String, String> entry = list.get(i);
			if (entry.getKey().equals(name)) {
				return entry.getValue();
			}
		}
		return null;
	}

	public static void encodeVal(Iterator<Map.Entry<String, String>> itr, Appendable sb) {
		try {
			while (true) {
				Map.Entry<String, String> entry = itr.next();
				sb.append(entry.getKey());
				String v = entry.getValue();

				f:
				if (v != null) {
					sb.append('=');
					for (int i = 0; i < v.length(); i++) {
						switch (v.charAt(i)) {
							case ' ':
							case ',':
							case '=':
								sb.append('"').append(v).append('"');
								break f;
							case '"':
								throw new IllegalArgumentException("'\"' Should not occur");
						}
					}
					sb.append(v);
				}

				if (!itr.hasNext()) break;
				sb.append(',');
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}
	@SuppressWarnings("fallthrough")
	public static void decodeVal(CharSequence field, BiConsumer<String,String> kvs) {
		String k = null;
		int i = 0, j = 0;
		int flag = 0;
		while (i < field.length()) {
			char c = field.charAt(i++);
			if (flag == 1) {
				if (c == '"') {
					if (k == null) throw new IllegalArgumentException("Escaped key");
					kvs.accept(k, Sub(field, j, i - 1));
					j = i;
					k = null;
					flag = 2;
				}
			} else {
				switch (c) {
					case '=':
						if (k != null) throw new IllegalArgumentException("Unexpected '='");
						k = Sub(field, j, i - 1).toLowerCase(Locale.ROOT);
						j = i;
						flag = 0;
						break;
					case '"':
						flag = 1;
						j = i;
						break;
					case ' ':
					case '\t':
						c = ' ';
						while (true) {
							int c1 = field.charAt(i);
							if (c1 != ' ' && c1 != '\t') break;
							i++;
						}
					case ',':
					case ';':
						if (k != null) {
							kvs.accept(k, Sub(field, j, i - 1));
							k = null;
						} else {
							if (i - 1 > j) kvs.accept(Sub(field, j, i - 1).toLowerCase(Locale.ROOT), "");
							else if (c != ' ' && (flag & 2) == 0) throw new IllegalArgumentException("'" + c + "' on empty");
						}
						flag = 0;
						j = i;
						break;
				}
			}
		}

		if (k != null) {
			kvs.accept(k, Sub(field, j, i));
		} else if (i > j) {
			kvs.accept(Sub(field, j, i).toLowerCase(Locale.ROOT), "");
		}
	}

	private static String Sub(CharSequence c, int s, int e) {
		return c.subSequence(s, e).toString();
	}

	public Headers() {}
	public Headers(CharSequence data) throws ParseException {
		for (String line : new LineReader(data, true)) {
			int pos = line.indexOf(':');
			put(line.substring(0, pos), line.substring(pos + 1).trim());
		}
	}

	public boolean parseHead(DynByteBuf buf) {
		return parseHead(buf, new ByteList(32));
	}
	public boolean parseHead(DynByteBuf buf, ByteList tmp) {
		int i = buf.rIndex;
		int len = i+buf.readableBytes();

		while (true) {
			int prevI = i;
			while (buf.get(i) != ':') {
				if (++i == len) return false;
			}

			if (i+2 >= len) return false;

			tmp.clear();
			String key = dedup.find(lower(tmp.put(buf, prevI, i-prevI))).toString();

			if (buf.get(++i) != ' ') throw new IllegalArgumentException("header+"+i+": 键未以': '结束");
			++i;

			tmp.clear();
			prevI = i;
			while (true) {
				int c = buf.get(i);
				if (c == '\r') {
					if (i+2 >= len) return false;

					if (buf.get(i+1) != '\n') continue;

					tmp.put(buf, prevI, i-prevI);

					i += 2;
					prevI = i;

					c = buf.get(i);
					// mht
					if (c == ' ' || c == '\t') {
						tmp.put((byte) c);
						continue;
					}

					String val = tmp.readAscii(tmp.readableBytes()); tmp.clear();
					checkVal(val);

					put(key, val);

					if (c == '\r') {
						if (i+1 >= len) return false;

						if (buf.get(i+1) != '\n')
							throw new IllegalArgumentException("header+"+(i+1)+": 应当以'\\r\\n\\r\\n'结束");

						buf.rIndex = i+2;
						return true;
					}

					buf.rIndex = i;
					break;
				}

				if (++i == len) return false;
			}
		}
	}

	protected Entry<CharSequence, String> createEntry(CharSequence key) {
		return new E(key);
	}

	public List<String> getAll(final String s) {
		E e = (E) getEntry(s);
		if (e == null) return null;

		SimpleList<String> list = new SimpleList<>(e.all.size()+1);
		list.add(e.v);
		list.addAll(e.all);
		return list;
	}

	@Override
	protected void putRemovedEntry(Entry<CharSequence, String> entry) {
		E e = (E) entry;
		e.all.clear();
		super.putRemovedEntry(e);
	}

	public Entry<CharSequence, String> getEntry(CharSequence key) {
		return super.getEntry(lower(key));
	}
	public Entry<CharSequence, String> getOrCreateEntry(CharSequence key) {
		key = lower(key);
		Entry<CharSequence, String> entry = super.getOrCreateEntry(key);
		if (entry.v == IntMap.UNDEFINED) {
			entry.k = dedup.find(key).toString();
			checkKey(entry.k);
		}
		return entry;
	}

	public String getAt(String key, int index) {
		final E e = (E) getEntry(key);
		if (e == null) return null;
		return (index == 0) ? e.v : e.all.get(index - 1);
	}

	public int getCount(String key) {
		E e = (E) getEntry(key);
		if (e == null) return 0;
		return 1+e.all.size();
	}

	@Override
	public String put(CharSequence key, String value) {
		checkVal(value);
		return super.put(key, value);
	}

	public void add(String key, String value) {
		checkVal(value);

		E e = (E) getOrCreateEntry(key);
		if (e.v == IntMap.UNDEFINED) {
			e.v = value;
			size++;
		} else {
			if (e.all.isEmpty()) e.all = new LinkedList<>();
			e.all.add(value);
		}
	}

	public void set(String key, String value) {
		checkVal(value);

		E e = (E) getOrCreateEntry(key);
		if (e.v == IntMap.UNDEFINED) size++;
		e.all = Collections.emptyList();
		e.v = value;
	}

	public void set(String key, List<String> value) {
		for (int i = 0; i < value.size(); i++)
			checkVal(value.get(i));

		E e = (E) getOrCreateEntry(key);
		if (e.v == IntMap.UNDEFINED) size++;
		if (value.size() == 1) {
			e.all = Collections.emptyList();
		} else {
			e.all = new ArrayList<>(value);
			e.all.remove(0);
		}
		e.v = value.get(0);
	}

	private static CharSequence lower(CharSequence key) {
		int len = key.length();
		for (int i = 0; i < len; i++) {
			char c = key.charAt(i);
			if (c >= 'A' && c <= 'Z') {
				CharList tmp = IOUtil.getSharedCharBuf().append(key);
				char[] cs = tmp.list;

				while (i < len) {
					c = cs[i];
					if (c >= 'A' && c <= 'Z') cs[i] = (char) (c+32);
					i++;
				}

				return tmp;
			}
		}
		return key;
	}

	private static final MyHashSet<CharSequence> dedup = new MyHashSet<>();
	static {
		F("accept-charset");F("accept-encoding");
		F("accept-language");F("accept-ranges");F("accept");F("access-control-allow-origin");F("age");F("allow");
		F("authorization");F("cache-control");F("content-disposition");F("content-encoding");F("content-language");
		F("content-length");F("content-location");F("content-range");F("content-type");F("cookie");F("date");F("etag");
		F("expect");F("expires");F("from");F("host");F("if-match");F("if-modified-since");F("if-none-match");F("if-range");
		F("if-unmodified-since");F("last-modified");F("link");F("location");F("max-forwards");F("proxy-authenticate");
		F("proxy-authorization");F("range");F("referer");F("refresh");F("retry-after");F("server");F("set-cookie");
		F("strict-transport-security");F("transfer-encoding");F("user-agent");F("vary");F("via");F("www-authenticate");
	}
	private static void F(String s) { dedup.add(s); }

	public void encode(Appendable sb) {
		try {
			for (Map.Entry<CharSequence, String> entry : entrySet()) {
				sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");

				E e = (E) entry;
				List<String> all = e.all;
				for (int i = 0; i < all.size(); i++) {
					sb.append(entry.getKey()).append(": ").append(all.get(i)).append("\r\n");
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}

	private static void checkKey(CharSequence key) {
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (c < 0x20 || c == 0x3a || c >= 0x7f) {
				illegalChar(key, i, c);
			}
		}
	}

	private static void checkVal(CharSequence val) {
		for (int i = 0; i < val.length(); i++) {
			char c = val.charAt(i);
			if ((c < 0x20 || c >= 0x7f) && c != '\t') {
				illegalChar(val, i, c);
			}
		}
	}

	private static void illegalChar(CharSequence key, int i, char c) {
		throw new IllegalArgumentException("HTTP头/无效字符 #"+(int)c+" 偏移 "+i+" 内容 "+ITokenizer.addSlashes(key));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		encode(sb);
		return sb.toString();
	}
}