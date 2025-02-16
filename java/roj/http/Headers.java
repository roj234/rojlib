package roj.http;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.concurrent.OperationDone;
import roj.config.Tokenizer;
import roj.http.server.HttpCache;
import roj.io.IOUtil;
import roj.reflect.ReflectionUtils;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.text.Escape;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.function.BiConsumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author solo6975
 * @since 2021/10/23 21:20
 */
public class Headers extends MyHashMap<CharSequence, String> {
	public static void encodeVal(Iterator<Map.Entry<String, String>> itr, Appendable sb) {
		if (!itr.hasNext()) return;
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

	public static String getOneValue(CharSequence field, String key) {
		E vo = new E();
		vo.k = key;
		try {
			complexValue(field, vo, false);
		} catch (OperationDone e) {
			return Helpers.cast(vo.k);
		}
		return null;
	}
	public static List<Map.Entry<String, String>> getValues(CharSequence field) {
		List<Map.Entry<String, String>> kvs = new SimpleList<>();
		complexValue(field, (k, v) -> kvs.add(new SimpleImmutableEntry<>(k, v)), false);
		return kvs;
	}

	public static Map<String, String> simpleValue(CharSequence query, String delim, boolean throwOrSkip) throws MalformedURLException {
		List<String> queries = TextUtil.split(query, delim);
		MyHashMap<String, String> map = new MyHashMap<>(queries.size());

		for (int i = 0; i < queries.size(); i++) {
			String s = queries.get(i);
			int j = s.indexOf('=');
			try {
				if (j == -1) map.put(Escape.decodeURI(s), "");
				else map.put(Escape.decodeURI(s.substring(0, j)), Escape.decodeURI(s.substring(j+1)));
			} catch (MalformedURLException e) {
				if (throwOrSkip) throw e;
			}
		}
		return map;
	}
	@SuppressWarnings("fallthrough")
	public static void complexValue(CharSequence field, BiConsumer<String,String> kvs, boolean throwOrSkip) {
		try {
		String k = null;
		int i = 0, j = 0;
		int flag = 0;
		while (i < field.length()) {
			char c = field.charAt(i++);
			if (flag == 1) {
				if (c == '"') {
					if (k == null) throw new IllegalArgumentException("Escaped key");
					kvs.accept(Escape.decodeURI(k), Sub(field, j, i - 1));
					j = i;
					k = null;
					flag = 2;
				}
			} else {
				switch (c) {
					case '=':
						if (k != null) throw new IllegalArgumentException("Unexpected '='");
						k = Sub(field, j, i - 1);
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
							kvs.accept(Escape.decodeURI(k), Sub(field, j, i - 1));
							k = null;
						} else {
							if (i - 1 > j) kvs.accept(Sub(field, j, i - 1), "");
							else if (c != ' ' && (flag & 2) == 0) throw new IllegalArgumentException("'" + c + "' on empty");
						}
						flag = 0;
						j = i;
						break;
				}
			}
		}

		if (k != null) {
			kvs.accept(Escape.decodeURI(k), Sub(field, j, i));
		} else if (i > j) {
			kvs.accept(Sub(field, j, i).toLowerCase(Locale.ROOT), "");
		}
		} catch (MalformedURLException e) {
			if (throwOrSkip) Helpers.athrow(e);
		}
	}
	private static String Sub(CharSequence c, int s, int e) throws MalformedURLException { return Escape.decodeURI(c.subSequence(s, e)); }

	public Headers() { super(4); }
	public Headers(CharSequence data) { putAllS(data); }
	public Headers(Map<CharSequence, String> data) {
		putAll(data);
	}

	public boolean parseHead(DynByteBuf buf) {
		return parseHead(buf, new ByteList(32));
	}
	public boolean parseHead(DynByteBuf buf, ByteList tmp) {
		if (!buf.isReadable()) return false;

		int i = buf.rIndex;
		int len = buf.wIndex();

		while (true) {
			tmp.clear();
			while (true) {
				int c = buf.get(i);
				if (c == ':') break;
				if (c >= 'A' && c <= 'Z') c += 32;
				tmp.append((char) c);
				if (++i == len) return false;
			}

			if (i+2 >= len) return false;

			String key = dedup.find(tmp).toString();

			// a:b也可行
			if (buf.get(++i) == ' ') ++i;

			tmp.clear();
			int prevI = i;
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

					String val = tmp.readUTF(tmp.readableBytes()); tmp.clear();
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
	public void putAllS(CharSequence data) {
		for (String line : LineReader.create(data)) {
			if (line.isEmpty()) continue;

			int pos = line.indexOf(':');
			put(line.substring(0, pos), line.substring(pos+1).trim());
		}
	}

	public final List<Cookie> getCookieFromServer() {
		String field = getField("set-cookie");
		if (field.isEmpty()) return Collections.emptyList();

		List<Cookie> cookies = new SimpleList<>();
		complexValue(field, new BiConsumer<>() {
            Cookie cookie;

            @Override
            public void accept(String k, String v) {
                if (cookie != null && cookie.read(k, v)) return;

                try {
                    if (cookie != null) cookie.clearDirty();
                    cookie = new Cookie(Escape.decodeURI(k), Escape.decodeURI(v));
                    cookies.add(cookie);
                } catch (MalformedURLException e) {
                    cookie = new Cookie("invalid");
                }
            }
        }, false);

		int i = cookies.size()-1;
		if (i >= 0) cookies.get(i).clearDirty();

		return cookies;
	}
	public final Map<String, String> getCookieFromClient() throws MalformedURLException {
		String field = getField("cookie");
		if (field.isEmpty()) return Collections.emptyMap();
		return simpleValue(field, "; ", true);
	}
	public final void sendCookieToClient(List<Cookie> cookies) {
		if (cookies.isEmpty()) return;

		E entry = (E) getOrCreateEntry("set-cookie");
		if (entry.k == UNDEFINED) {
			entry.k = "set-cookie";
			size++;
		}

		CharList sb = new CharList();
		cookies.get(0).write(sb, true);
		entry.setValue(sb.toString());

		if (cookies.size() > 1) {
			entry.all = new SimpleList<>(cookies.size()-1);
			for (int i = 1; i < cookies.size(); i++) {
				sb.clear();
				cookies.get(i).write(sb, true);
				entry.all.add(sb.toString());
			}
		}

		sb._free();
	}
	public final void sendCookieToServer(Collection<Cookie> cookies) {
		if (cookies.isEmpty()) return;

		Iterator<Cookie> itr = cookies.iterator();

		CharList sb = new CharList();
		while (true) {
			itr.next().write(sb, false);
			if (!itr.hasNext()) break;
			sb.append("; ");
		}

		put("cookie", sb.toStringAndFree());
	}

	public String getField(String key) { return getOrDefault(key,""); }

	public String getFieldValue(String key, String minorKey) { return getOneValue(getField(key),minorKey); }
	public List<Map.Entry<String,String>> getFieldValue(String key) { return getValues(getField(key)); }
	public void getFieldValue(String key, BiConsumer<String, String> consumer) { complexValue(getField(key),consumer,false); }

	// region just a simple multi-value lowercase map

	private static final class E extends MyHashMap.Entry<CharSequence, String> implements BiConsumer<String, String> {
		List<String> all = Collections.emptyList();

		// awa, 省一个类, 我真无聊
		public final void accept(String k, String v) {
			if (this.k.equals(k)) {
				this.k = v;
				throw OperationDone.INSTANCE;
			}
		}
	}

	public final List<String> getAll(final String s) {
		E e = (E) getEntry(s);
		if (e == null) return null;

		SimpleList<String> list = new SimpleList<>(e.all.size()+1);
		list.add(e.v);
		list.addAll(e.all);
		return list;
	}


	public final String getAt(String key, int index) {
		final E e = (E) getEntry(key);
		if (e == null) return null;
		return (index == 0) ? e.v : e.all.get(index - 1);
	}

	public final int getCount(String key) {
		E e = (E) getEntry(key);
		if (e == null) return 0;
		return 1+e.all.size();
	}

	// region lowerMap
	public AbstractEntry<CharSequence, String> getEntry(Object key) {return super.getEntry(lower(key.toString())); }
	public AbstractEntry<CharSequence, String> getOrCreateEntry(CharSequence key) { return super.getOrCreateEntry(lower(key)); }

	@Override
	protected AbstractEntry<CharSequence, String> useEntry() {
		E entry = Helpers.cast(HttpCache.getInstance().headers.pop());

		if (entry == null) entry = new E();
		entry.k = entry.v = Helpers.cast(UNDEFINED);
		return entry;
	}
	@Override
	protected void onDel(AbstractEntry<CharSequence, String> entry) {
		E e = (E) entry;
		e.k = null;
		e.next = null;
		e.all.clear();
		HttpCache.getInstance().headers.add(e);
	}

	@Override
	protected void onPut(AbstractEntry<CharSequence, String> entry, String newV) {
		checkVal(newV);

		if (entry.getValue() == UNDEFINED) {
			entry.k = dedup.find(lower(entry.k)).toString();
			checkKey(entry.k);
		}
	}
	// endregion

	public final void add(String key, String value) {
		value = checkVal(value);

		E e = (E) getOrCreateEntry(key);
		if (e.k == UNDEFINED) {
			e.k = lower(key).toString();
			e.v = value;
			size++;
		} else {
			if (e.all.isEmpty()) e.all = new LinkedList<>();
			e.all.add(value);
		}
	}

	public final void set(String key, String value) {
		value = checkVal(value);

		E e = (E) getOrCreateEntry(key);
		if (e.k == UNDEFINED) {
			e.k = lower(key);
			size++;
		}
		e.all = Collections.emptyList();
		e.v = value;
	}

	public final void set(String key, List<String> value) {

		E e = (E) getOrCreateEntry(key);
		if (e.k == UNDEFINED) {
			e.k = lower(key);
			size++;
		}
		if (value.size() == 1) {
			e.all = Collections.emptyList();
		} else {
			e.all = new ArrayList<>(value);
			e.all.remove(0);
			for (int i = 0; i < e.all.size(); i++)
				e.all.set(i, checkVal(e.all.get(i)));
		}
		e.v = checkVal(value.get(0));
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

	// endregion

	public final void encode(Appendable sb) {
		try {
			for (var it = entrySet().iterator(); it.hasNext(); ) {
				E entry = (E) it.next();
				var key = entry.getKey();

				char c = key.charAt(0);
				int offset = c == ':' || c == '*' || c == '^' ? 1 : 0;

				h(sb, key, offset, entry.getValue());

				List<String> all = entry.all;
				for (int i = 0; i < all.size(); i++) {
					h(sb, key, offset, all.get(i));
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}
	private static Appendable h(Appendable sb, CharSequence key, int off, String value) throws IOException {return sb.append(key, off, key.length()).append(value.isEmpty()?":":": ").append(value).append("\r\n");}

	private static void checkKey(CharSequence key) {
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (c <= 0x20 || c == 0x3a || c >= 0x7f) {
				if (i == 0 && c == ':' && key.length() > 1) continue;
				illegalChar(key, i, c);
			}
		}
	}
	private static String checkVal(String val) {
		int found = -1;
		for (int i = 0; i < val.length(); i++) {
			var c = val.charAt(i);
			if (c == 0 || c == '\r' || c == '\n') illegalChar(val, i, c);

			if (c == ' ' || c == '\t') {
				if (found < 0) illegalChar(val, i, c);
			} else {
				found = i;
			}
		}
		if (found != val.length()-1) return val.substring(0, found+1);
		return val;
	}
	private static void illegalChar(CharSequence key, int i, char c) {throw new IllegalArgumentException("HTTP头/无效字符 #"+(int)c+" 偏移 "+i+" 内容 "+Tokenizer.addSlashes(key));}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		encode(sb);
		return sb.toString();
	}

	private ToIntMap<String> encType;
	public int getEncType(String name) {return encType == null ? 0 : encType.getOrDefault(name, 0);}
	public void setEncType(String name, int enc) {
		if (encType == null) encType = new ToIntMap<>();
		encType.putInt(name, enc);
	}

	private static final long LENGTH_OFFSET = ReflectionUtils.fieldOffset(MyHashMap.class, "length");
	public void _moveFrom(Headers head) {
		entries = null;
		if (head.entries != null) {
			Unaligned.U.putInt(this, LENGTH_OFFSET, 0);
			ensureCapacity(head.entries.length);
			entries = head.entries;
		}
		size = head.size;
		encType = head.encType;

		head.entries = null;
		head.size = 0;
		head.encType = null;
	}
}