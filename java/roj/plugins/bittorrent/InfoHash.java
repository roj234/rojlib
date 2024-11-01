package roj.plugins.bittorrent;

import roj.collect.Int2IntMap;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static roj.config.JSONParser.parseNumber;

/**
 * InfoHash
 * @author Roj234
 */
final class InfoHash {
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

	private MessageDigest sha1 = MessageDigest.getInstance("SHA1");
	private Source source;
	private InputStream in;
	private ByteList buf = new ByteList(64);

	public InfoHash() throws NoSuchAlgorithmException {}

	public byte[] getInfoHash(File file) throws IOException {
		try (var in = new FileSource(file, false)) {
			return getInfoHash(in);
		}
	}
	public byte[] getInfoHash(Source in) throws IOException {
		source = in;
		in.seek(0);
		this.in = in.asInputStream();
		try {
			element();
		} catch (OperationDone ignored) {}
		this.in = null;
		source = null;
		return sha1.digest();
	}

	private boolean element() throws IOException {
		int c = in.read();
		switch (BEN_C2C.getOrDefaultInt(c, -2)) {
			default -> throw new IOException("无效的字符 "+c);
			case -1 -> throw new IOException("未预料的EOF ");
			case 0 -> {return false;}
			case 1 -> list();
			case 2 -> map();
			case 3 -> readInt(-1);
			case 4 -> readString(c);
		}
		return true;
	}
	private void list() throws IOException {
		while (element());
	}
	private void map() throws IOException {
		while (true) {
			int c = in.read();
			int t = BEN_C2C.getOrDefaultInt(c, -2);
			if (t == 0) break;
			else if (t != 4) throw new IOException("未预料的字符 "+c);

			boolean isInfoBlock = readString(c);
			if (isInfoBlock) {
				var start = source.position();
				element();
				var count = source.position()-start;

				source.seek(start);
				buf.clear();
				source.read(buf, (int) count);
				sha1.update(buf.list, 0, (int) count);
				throw OperationDone.INSTANCE;
			}

			if (!element()) throw new IOException("未预料的END");
		}
	}
	private boolean readString(int c) throws IOException {
		var count = readInt(c);
		if (count == 4) {
			buf.readStream(in, 4);
			return buf.readAscii(4).equals("info");
		}
		IOUtil.skipFully(in, count);
		return false;
	}
	private long readInt(int c) throws IOException {
		if (c < 0) c = in.read();

		boolean neg = c == '-';
		if (neg || c == '+') {
			c = in.read();
		}

		if (c == 'e' || c == ':') throw new IOException("空的数字");

		CharList t = IOUtil.getSharedCharBuf();
		while (c >= 0) {
			if (c == 'e' || c == ':') return parseNumber(t, 4, neg);

			t.append((char) c);
			c = in.read();
		}

		throw new EOFException();
	}
}