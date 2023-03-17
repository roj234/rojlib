package roj.config;

import roj.collect.Int2IntMap;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.config.data.*;
import roj.config.exch.TByteArray;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static roj.config.JSONParser.*;

/**
 * bittorrent解析器
 *
 * @author Roj234
 */
public class BencodeParser {
	public static final int SKIP_UTF_DECODE = 2;

	private static final Int2IntMap BEN_C2C = new Int2IntMap();
	static {
		BEN_C2C.putInt(-1, -1);
		BEN_C2C.putInt('e', 0);
		BEN_C2C.putInt('l', 1);
		BEN_C2C.putInt('d', 2);
		BEN_C2C.putInt('i', 3);

		String s = "1234567890";
		for (int i = 0; i < s.length(); i++) {
			BEN_C2C.putInt(s.charAt(i), 4);
		}
	}

	public static CEntry parse(InputStream cs) throws IOException, ParseException {
		return new BencodeParser().parse(cs, 0);
	}

	public CEntry parse(InputStream in, int flags) throws IOException, ParseException {
		this.in = in;
		try {
			return el((byte) flags);
		} catch (ParseException e) {
			throw e.addPath("$");
		}
	}

	public int acceptableFlags() {
		return NO_DUPLICATE_KEY|NO_EOF|ORDERED_MAP;
	}

	private InputStream in;
	private final ByteList buf = new ByteList(64);

	private CEntry el(int flag) throws IOException, ParseException {
		int c = in.read();
		switch (BEN_C2C.getOrDefaultInt(c, -2)) {
			default: throw err("无效的字符 " + c);
			case -1: throw err("未预料的EOF ");
			case 0: return null; // END e
			case 1: return list(flag); // LIST l
			case 2: return map(flag); // DICT d
			case 3: // INT i
				long v = readInt(-1);
				return v < Integer.MIN_VALUE || v > Integer.MAX_VALUE ? CLong.valueOf(v) : CInteger.valueOf((int) v);
			case 4: // STRING <number>
				ByteList b = readString(c);
				if ((flag & SKIP_UTF_DECODE) == 0) {
					try {
						return CString.valueOf(b.readUTF(b.readableBytes()));
					} catch (IllegalArgumentException ignored) {}
				}
				return new TByteArray(b.toByteArray());
		}
	}
	private CList list(int flag) throws IOException, ParseException {
		CList list = new CList();

		while (true) {
			try {
				CEntry el = el(flag);
				if (el == null) break;
				list.add(el);
			} catch (ParseException e) {
				throw e.addPath("[" + list.size() + "]");
			}
		}

		return list;
	}
	private CMapping map(int flag) throws IOException, ParseException {
		CMapping map = new CMapping((flag & ORDERED_MAP) != 0 ? new LinkedMyHashMap<>() : new MyHashMap<>());

		while (true) {
			int c = in.read();
			int t = BEN_C2C.getOrDefaultInt(c, -2);
			if (t == 0) break;
			else if (t != 4) throw new IOException("未预料的字符 " + (char)c);

			ByteList b = readString(c);
			String k = b.readUTF(b.readableBytes());

			if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(k)) throw err("重复的key: " + k);

			try {
				CEntry el = el(flag);
				if (el == null) throw err("未预料的END");
				map.put(k, el);
			} catch (ParseException e) {
				throw e.addPath('.' + k);
			}
		}

		return map;
	}

	private ByteList readString(int c) throws IOException, ParseException {
		int num = (int) readInt(c);
		ByteList b = buf; b.clear();
		if (b.readStream(in, num) < num) throw err("未预料的EOF");
		return b;
	}
	private long readInt(int c) throws IOException, ParseException {
		if (c < 0) c = in.read();

		boolean neg = c == '-';
		if (neg || c == '+') {
			c = in.read();
		}

		if (c == 'e' || c == ':') throw err("空的数字");

		CharList t = IOUtil.getSharedCharBuf();
		while (c >= 0) {
			if (c == 'e' || c == ':') {
				return parseNumber(t, TextUtil.LONG_MAXS, 10, neg);
			}
			t.append((char) c);
			c = in.read();
		}

		throw new EOFException();
	}

	private ParseException err(String v) {
		return new ParseException(v, 0);
	}
}
