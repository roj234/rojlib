package roj.http;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.*;
import roj.io.IOUtil;
import roj.text.*;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.function.Consumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author solo6975
 * @since 2021/10/23 21:20
 */
public class Headers extends Multimap<CharSequence, String> {
	public Headers() {this(8);}
	public Headers(int size) {super(size);}
	public Headers(CharSequence data) {parseFromText(data);}
	public Headers(Map<CharSequence, String> data) {putAll(data);}

	public static boolean parseHeader(Headers map, DynByteBuf buf) {
		var tmp = new ByteList(ArrayCache.getIOBuffer());
		boolean b = parseHeader(map, buf, tmp);
		tmp.release();
		return b;
	}
	/**
	 * 这个函数是流式的，它可以按行解析
	 */
	public static boolean parseHeader(Headers map, DynByteBuf buf, ByteList tmp) {
		if (!buf.isReadable()) return false;

		int i = buf.rIndex;
		int len = buf.wIndex();

		while (true) {
			tmp.clear();
			while (true) {
				int c = buf.getByte(i);
				if (c == ':') break;
				if (c >= 'A' && c <= 'Z') c += 32;
				tmp.append((char) c);
				if (++i == len) return false;
			}

			if (i+2 >= len) return false;

			String key = dedup.find(tmp).toString();

			// a:b也可行
			if (buf.getByte(++i) == ' ') ++i;

			tmp.clear();
			int prevI = i;
			while (true) {
				int c = buf.getByte(i);
				if (c == '\r') {
					if (i+2 >= len) return false;

					if (buf.getByte(i+1) != '\n') continue;

					tmp.put(buf, prevI, i-prevI);

					i += 2;
					prevI = i;

					c = buf.getByte(i);
					// mht
					if (c == ' ' || c == '\t') {
						tmp.put((byte) c);
						continue;
					}

					String val = tmp.readUTF(tmp.readableBytes()); tmp.clear();
					checkVal(val);

					if (LIST_TYPE.contains(key)) {
						addOWS(map, key, val);
					} else {
						map.add(key, val);
					}

					if (c == '\r') {
						if (i+1 >= len) return false;

						if (buf.getByte(i+1) != '\n')
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

	public static void addOWS(Headers map, String key, String val) {
		int prev = 0;
		int length = val.length();

		for (int i = 0; i < length;) {
			char c = val.charAt(i);
			if (c == ',') {
				map.add(key, val.substring(prev, i++));

				while (i < length) {
					if (val.charAt(i) == ' ') i++;
					else break;
				}

				prev = i;
			} else {
				if (c != '"') { i++; continue; }

				var isEscaped = false;
				for (;;) {
					if (++i == length) throw new IllegalArgumentException("Unterminated quoted-string at("+i+"): "+val);

					c = val.charAt(i);
					if (c == '\\') {
						isEscaped = true;
					} else if (isEscaped) {
						isEscaped = false;
					} else if (c == '"') {
						i++;
						break;
					}
				}
			}
		}

		map.add(key, val.substring(prev));
	}

	public void parseFromText(CharSequence data) {
		for (String line : LineReader.create(data)) {
			if (line.isEmpty()) continue;

			int pos = line.indexOf(':');
			put(line.substring(0, pos), line.substring(pos+1).trim());
		}
	}

	@NotNull public String header(String field) { return getOrDefault(field,""); }

	public static final class HeaderElement extends ListMap<String, String> {
		public HeaderElement() {super(new ArrayList<>(), new ArrayList<>());}
		public HeaderElement(String field) {this();HttpUtil.parseParameters(field, this::add);}
		public HeaderElement(List<String> keys, List<String> values) {super(keys, values);}

		public String getUnescaped(Object key) throws ParseException {
			String s = super.get(key);
			return s == null ? null : Tokenizer.unescape(s);
		}
		public String getDecoded(Object key) throws MalformedURLException {
			String s = super.get(key);
			return s == null ? null : URICoder.decodeURI(s);
		}

		public Iterable<String> getAll(String key) {
			return () -> new AbstractIterator<>() {
				int index;

				@Override
				protected boolean computeNext() {
					for (int i = index; i < keys.size(); i++) {
						if (keys.get(i).equals(key)) {
							result = values.get(i);
							index = i;
							return true;
						}
					}
					return false;
				}
			};
		}

		public void add(String key) {add(key, null);}
		public void add(String key, @Nullable String value) {
			keys.add(key);
			values.add(value);
		}

		@Override
		public String put(String key, @Nullable String value) {
			int index = keys.indexOf(key);
			if (index < 0) {
				keys.add(key);
				values.add(value);
				return null;
			} else {
				return values.set(index, value);
			}
		}

		@Override
		public String remove(Object key) {return remove((String) key, false);}
		public String remove(String key, boolean onlyLast) {
			String removed = null;

			for (int i = keys.size() - 1; i >= 0; i--) {
				var item = keys.get(i);
				if (key.equals(item)) {
					keys.remove(i);
					removed = values.remove(i);
					if (onlyLast) break;
				}
			}

			return removed;
		}

		@Override
		public void clear() {
			keys.clear();
			values.clear();
		}

		public void writeTo(CharList sb) {
			for (int i = 0; i < keys.size();) {
				var key = keys.get(i);
				var value = values.get(i);
				sb.append(key);

				if (value != null) {
					sb.append('=').append(value);
				}

				if (++i == keys.size()) break;
				sb.append(';');
			}
		}

		public String toString() {
			var sb = new CharList();
			writeTo(sb);
			return sb.toStringAndFree();
		}

		@UnknownNullability
		public String mainValue() {return values.isEmpty() ? null : values.get(0);}
	}

	@NotNull public HeaderElement getElement(String field) {return new HeaderElement(header(field));}
	@NotNull public HeaderElement findElement(String field, String token) {
		String fieldValue = findValue(field, token);
		return new HeaderElement(fieldValue == null ? "" : fieldValue);
	}
	public void forEachElement(String field, Consumer<HeaderElement> consumer) {
		var entry = (Entry<CharSequence, String>) getEntry(field);
		if (entry == null) return;

		consumer.accept(new HeaderElement(entry.value));
		for (String item : entry.rest) {
			consumer.accept(new HeaderElement(item));
		}
	}

	@Override
	public boolean containsKey(CharSequence field, String token) {
		String fieldValue = findValue(field, token);
		return fieldValue != null;
	}

	private String findValue(CharSequence field, String token) {
		var entry = (Entry<CharSequence, String>) getEntry(field);
		String fieldValue = null;
		if (entry != null) {
			if (tokenMatches(entry.value, token)) fieldValue = entry.value;
			else for (String item : entry.rest) {
				if (tokenMatches(item, token)) {
					fieldValue = item;
					break;
				}
			}
		}
		return fieldValue;
	}
	private static boolean tokenMatches(String a, String b) {
		boolean isPrefixMatch = b.endsWith("*");
		if (isPrefixMatch) return a.regionMatches(0, b, 0, b.length()-1);

		if (!a.startsWith(b)) return false;
		if (a.length() == b.length()) return true;
		char c = a.charAt(b.length());
		return c == ' ' || c == '\t' || c == ';';
	}

	/**
	 * 获取非列表字段的属性
	 * @param field 字段名称
	 * @param attribute 属性名称
	 * @return 属性值
	 */
	@Nullable
	public String getParameter(String field, String attribute) {
		assert !LIST_TYPE.contains(field);
		return HttpUtil.getParameter(header(field), attribute);
	}

	public long getContentLength() {return Long.parseLong(getOrDefault("content-length", "-1"));}
	public String getContentEncoding() {return getOrDefault("content-encoding", "identity").toLowerCase(Locale.ROOT);}

	// region lowerMap
	public AbstractEntry<CharSequence, String> getEntry(Object key) {return super.getEntry(lower(key.toString())); }
	public AbstractEntry<CharSequence, String> getOrCreateEntry(CharSequence key) { return super.getOrCreateEntry(lower(key)); }

	@Override
	protected void onPut(AbstractEntry<CharSequence, String> entry, String newV) {
		checkVal(newV);

		if (entry.getValue() == UNDEFINED) {
			entry.key = dedup.find(lower(entry.getKey())).toString();
			checkKey(entry.getKey());
		}
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public final void add(CharSequence key, String value) {
		value = checkVal(value);

		Entry e = (Entry) getOrCreateEntry(key);
		if (e.getKey() == UNDEFINED) {
			e.key = lower(key).toString();
			e.value = value;
			size++;
		} else {
			if (e.rest.isEmpty()) e.rest = new LinkedList<>();
			e.rest.add(value);
		}
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public final void set(CharSequence key, String value) {
		value = checkVal(value);

		Entry e = (Entry) getOrCreateEntry(key);
		if (e.getKey() == UNDEFINED) {
			e.key = lower(key);
			size++;
		}
		e.rest = Collections.emptyList();
		e.value = value;
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public final void set(CharSequence key, List<String> value) {
		Entry e = (Entry) getOrCreateEntry(key);
		if (e.getKey() == UNDEFINED) {
			e.key = lower(key);
			size++;
		}
		if (value.size() == 1) {
			e.rest = Collections.emptyList();
		} else {
			e.rest = new ArrayList(value);
			e.rest.remove(0);
			for (int i = 0; i < e.rest.size(); i++)
				e.rest.set(i, checkVal((String) e.rest.get(i)));
		}
		e.value = checkVal(value.get(0));
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

	private static final HashSet<CharSequence> LIST_TYPE = new HashSet<>();
	private static final HashSet<CharSequence> dedup = new HashSet<>();
	static {
		F("accept-charset");F("accept-encoding");
		F("accept-language");F("accept-ranges");F("accept");F("access-control-allow-origin");F("age");F("allow");
		F("authorization");F("cache-control");F("content-disposition");F("content-encoding");F("content-language");
		F("content-length");F("content-location");F("content-range");F("content-type");F("cookie");F("date");F("etag");
		F("expect");F("expires");F("from");F("host");F("if-match");F("if-modified-since");F("if-none-match");F("if-range");
		F("if-unmodified-since");F("last-modified");F("link");F("location");F("max-forwards");F("proxy-authenticate");
		F("proxy-authorization");F("range");F("referer");F("refresh");F("retry-after");F("server");F("set-cookie");
		F("strict-transport-security");F("transfer-encoding");F("user-agent");F("vary");F("via");F("www-authenticate");

		// RFC 9110
		C("trailer");
		C("connection");
		C("via");
		C("upgrade");
		C("content-encoding");
		C("content-language");
		C("te");
		C("allow");
		//C("www-authenticate");
		C("authentication-info");
		C("proxy-authenticate");
		C("proxy-authentication-info");
		C("accept");
		C("accept-charset");
		C("accept-encoding");
		C("accept-language");
		C("vary");

		// RFC 9111
		C("cache-control");

		// MDN
		C("access-control-allow-headers");
		C("access-control-allow-methods");
		C("access-control-allow-expose-headers");

		C("x-forwarded-for");
		C("forwarded");
	}
	private static void F(String s) { dedup.add(s); }
	private static void C(String s) { LIST_TYPE.add(s); }
	// endregion

	public final void encode(Appendable sb) {
		try {
			for (var it = entrySet().iterator(); it.hasNext(); ) {
				Entry<String, String> entry = Helpers.cast(it.next());
				var key = entry.getKey();

				char c = key.charAt(0);
				int offset = c == ':' || c == '*' || c == '^' ? 1 : 0;

				boolean canCombine = LIST_TYPE.contains(key);

				String value = entry.getValue();
				int valueLength = value.length();

				sb.append(key, offset, key.length()).append(value.isEmpty() ? ":" : ": ").append(value);

				List<String> rest = entry.rest;
				for (int i = 0; i < rest.size(); i++) {
					value = rest.get(i);

					if (canCombine && valueLength + value.length() <= 80) {
						sb.append(", ");
						valueLength += value.length();
					} else {
						sb.append("\r\n").append(key, offset, key.length()).append(": ");
						valueLength = 0;
					}

					sb.append(value);
				}

				sb.append("\r\n");
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}

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
	private static void illegalChar(CharSequence key, int i, char c) {throw new IllegalArgumentException("HTTP头/无效字符 #"+(int)c+" 偏移 "+i+" 内容 "+Tokenizer.escape(key));}

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

	public void _moveFrom(Headers head) {
		entries = null;
		if (head.entries != null) {
			mask = 1;
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