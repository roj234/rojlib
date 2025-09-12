package roj.config;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.Int2IntMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.ParseException;
import roj.util.ByteList;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static roj.config.JsonParser.parseNumber;

/**
 * bittorrent解析器
 * @author Roj234
 */
public final class BEncodeParser implements BinaryParser {
	public static final int SKIP_UTF_DECODE = 1;

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

	@Override
	public <T extends ValueEmitter> T parse(InputStream in, @MagicConstant(intValues = SKIP_UTF_DECODE) int flag, T emitter) throws IOException, ParseException {
		this.in = in;
		this.cc = emitter;
		try {
			element(flag);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			this.in = null;
			this.cc = null;
		}
		return emitter;
	}

	private InputStream in;
	private ValueEmitter cc;
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
					cc.emit(v);
				} else {
					cc.emit((int) v);
				}
				break;
			case 4: // STRING <number>
				ByteList b = readString(c);
				int r = b.rIndex;
				if ((flag & SKIP_UTF_DECODE) == 0) {
					try {
						cc.emit(b.readUTF(b.readableBytes()));
						break;
					} catch (IllegalArgumentException e) {
						b.rIndex = r;
					}
				}
				cc.emit(b.toByteArray());
				break;
		}
		return true;
	}
	private void list(int flag) throws IOException, ParseException {
		cc.emitList();
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
		cc.emitMap();

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
			if (c == 'e' || c == ':') return parseNumber(t, 4, neg);

			t.append((char) c);
			c = in.read();
		}

		throw new EOFException();
	}

	private ParseException err(String v) {
		return new ParseException("", v, 0);
	}
}