package roj.config;

import roj.collect.Int2IntMap;
import roj.config.data.CEntry;
import roj.config.serial.CVisitor;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static roj.config.JSONParser.*;

/**
 * bittorrent解析器
 * @author Roj234
 */
public final class BencodeParser implements BinaryParser {
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

	public static CEntry parses(InputStream in) throws IOException, ParseException {
		return new BencodeParser().parseRaw(in, 0);
	}

	@Override
	public <T extends CVisitor> T parseRaw(InputStream in, T cc, int flag) throws IOException, ParseException {
		this.in = in;
		this.cc = cc;
		try {
			element(flag);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			this.in = null;
			this.cc = null;
		}
		return cc;
	}

	public void serialize(CEntry entry, DynByteBuf out) throws IOException { entry.toB_encode(out); }
	public int availableFlags() { return NO_DUPLICATE_KEY|SKIP_UTF_DECODE|ORDERED_MAP; }
	public String format() { return "B-encode"; }

	private InputStream in;
	private CVisitor cc;
	private final ByteList buf = new ByteList(64);

	boolean element(int flag) throws IOException, ParseException {
		int c = in.read();
		switch (BEN_C2C.getOrDefaultInt(c, -2)) {
			default: throw err("无效的字符 " + c);
			case -1: throw err("未预料的EOF ");
			case 0: return false; // END e
			case 1: list(flag); break; // LIST l
			case 2: map(flag); break; // DICT d
			case 3: // INT i
				long v = readInt(-1);
				if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
					cc.value(v);
				} else {
					cc.value((int) v);
				}
				break;
			case 4: // STRING <number>
				ByteList b = readString(c);
				if ((flag & SKIP_UTF_DECODE) == 0) {
					try {
						cc.value(b.readUTF(b.readableBytes()));
					} catch (IllegalArgumentException ignored) {}
				}
				cc.value(b.toByteArray());
				break;
		}
		return true;
	}
	private void list(int flag) throws IOException, ParseException {
		cc.valueList();
		int size = 0;

		while (true) {
			try {
				if (!element(flag)) break;
			} catch (ParseException e) {
				throw e.addPath("["+size+"]");
			}
			size++;
		}
		cc.pop();
	}
	private void map(int flag) throws IOException, ParseException {
		cc.valueMap();

		while (true) {
			int c = in.read();
			int t = BEN_C2C.getOrDefaultInt(c, -2);
			if (t == 0) break;
			else if (t != 4) throw new IOException("未预料的字符 " + (char)c);

			ByteList b = readString(c);
			String k = b.readUTF(b.readableBytes());

			cc.key(k);
			try {
				if (!element(flag)) throw err("未预料的END");
			} catch (ParseException e) {
				throw e.addPath('.'+k);
			}
		}

		cc.pop();
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
