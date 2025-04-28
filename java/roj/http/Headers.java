package roj.http;

import org.jetbrains.annotations.NotNull;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.concurrent.OperationDone;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.reflect.ReflectionUtils;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.text.Escape;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.util.ArrayCache;
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
public class Headers extends Multimap<CharSequence, String> {
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
		var vo = new Entry<>();
		vo.k = key;
		try {
			complexValue(field, vo, false);
		} catch (OperationDone e) {
			return Helpers.cast(vo.k);
		}
		return null;
	}
	private static List<Map.Entry<String, String>> getValues(CharSequence field) {
		List<Map.Entry<String, String>> kvs = new SimpleList<>();
		complexValue(field, (k, v) -> kvs.add(new SimpleImmutableEntry<>(k, v)), false);
		return kvs;
	}

	@Deprecated
	public static Multimap<String, String> simpleValue(CharSequence query, String delim, boolean throwOrSkip) throws MalformedURLException {
		List<String> queries = TextUtil.split(query, delim);
		Multimap<String, String> map = new Multimap<>(queries.size());

		for (int i = 0; i < queries.size(); i++) {
			String s = queries.get(i);
			int j = s.indexOf('=');
			try {
				if (j == -1) map.add(Escape.decodeURI(s), "");
				else map.add(Escape.decodeURI(s.substring(0, j)), Escape.decodeURI(s.substring(j+1)));
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

	public Headers() {this(8);}
	public Headers(int size) {super(size);}
	public Headers(CharSequence data) {putAllS(data);}
	public Headers(Map<CharSequence, String> data) {putAll(data);}

	public static boolean parseHeader(Headers map, DynByteBuf buf) {
		var tmp = new ByteList(ArrayCache.getByteArray(256, false));
		boolean b = parseHeader(map, buf, tmp);
		tmp._free();
		return b;
	}
	public static boolean parseHeader(Headers map, DynByteBuf buf, ByteList tmp) {
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

					map.add(key, val);

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

	@NotNull public String header(String key) { return getOrDefault(key,""); }

	public String getFirstHeaderValue(String key) { return getOneValue(header(key),null); }
	public String getHeaderValue(String key, String minorKey) { return getOneValue(header(key),minorKey); }
	public List<Map.Entry<String,String>> getHeaderValues(String key) { return getValues(header(key)); }
	public void getHeaderValues(String key, BiConsumer<String, String> consumer) { complexValue(header(key),consumer,false); }

	public long getContentLength() {return Long.parseLong(getOrDefault("content-length", "-1"));}
	public String getContentEncoding() {return getOrDefault("content-encoding", "identity").toLowerCase(Locale.ROOT);}

	// region lowerMap
	public AbstractEntry<CharSequence, String> getEntry(Object key) {return super.getEntry(lower(key.toString())); }
	public AbstractEntry<CharSequence, String> getOrCreateEntry(CharSequence key) { return super.getOrCreateEntry(lower(key)); }

	@Override
	protected void onPut(AbstractEntry<CharSequence, String> entry, String newV) {
		checkVal(newV);

		if (entry.getValue() == UNDEFINED) {
			entry.k = dedup.find(lower(entry.k)).toString();
			checkKey(entry.k);
		}
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public final void add(CharSequence key, String value) {
		value = checkVal(value);

		Entry e = (Entry) getOrCreateEntry(key);
		if (e.k == UNDEFINED) {
			e.k = lower(key).toString();
			e.v = value;
			size++;
		} else {
			if (e.rest.isEmpty()) e.rest = new LinkedList<>();
			e.rest.add(value);
		}
	}

	@Override
	@SuppressWarnings({"rawtypes"})
	public final void set(CharSequence key, String value) {
		value = checkVal(value);

		Entry e = (Entry) getOrCreateEntry(key);
		if (e.k == UNDEFINED) {
			e.k = lower(key);
			size++;
		}
		e.rest = Collections.emptyList();
		e.v = value;
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public final void set(CharSequence key, List<String> value) {
		Entry e = (Entry) getOrCreateEntry(key);
		if (e.k == UNDEFINED) {
			e.k = lower(key);
			size++;
		}
		if (value.size() == 1) {
			e.rest = Collections.emptyList();
		} else {
			e.rest = new SimpleList(value);
			e.rest.remove(0);
			for (int i = 0; i < e.rest.size(); i++)
				e.rest.set(i, checkVal((String) e.rest.get(i)));
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
				Entry<String, String> entry = Helpers.cast(it.next());
				var key = entry.getKey();

				char c = key.charAt(0);
				int offset = c == ':' || c == '*' || c == '^' ? 1 : 0;

				h(sb, key, offset, entry.getValue());

				List<String> all = entry.rest;
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
	public int _getEncodeType(String name) {return encType == null ? 0 : encType.getOrDefault(name, 0);}
	public void _setEncodeType(String name, int enc) {
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