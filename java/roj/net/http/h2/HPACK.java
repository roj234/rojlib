package roj.net.http.h2;

import org.jetbrains.annotations.Nullable;
import roj.collect.MyHashSet;
import roj.collect.RingBuffer;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.BitBuffer;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.List;

import static roj.net.http.h2.H2Exception.ERROR_COMPRESS;
import static roj.net.http.h2.H2Exception.ERROR_PROTOCOL;

/**
 * @author Roj234
 * @since 2022/10/8 0008 6:52
 */
final class HPACK {
	private static Field F(String k) {return F(k, "");}
	private static Field F(String k, String v) {return new Field(k, v);}
	static final class Field {
		int id;
		CharSequence k,v;
		public Field() {}
		public Field(CharSequence k, CharSequence v) {
			this.k = k;
			this.v = v;
		}

		int len() {return 32+k.length()+v.length();}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Field field = (Field) o;

			if (!k.equals(field.k)) return false;
			return v.equals(field.v);
		}

		@Override
		public int hashCode() {return 31 * k.hashCode() + v.hashCode();}

		@Override
		public String toString() {return "Field{"+k+"='"+v+'\''+'}';}
	}
	private static final class Table extends RingBuffer<Field> {
		HPACK owner;
		int size, cap;

		public Table(int capacity) {
			super(256, false);
			cap = capacity;
		}

		public Field getField(int id) throws H2Exception {
			if (--id < STATIC_TABLE.size()) return STATIC_TABLE.get(id);
			id -= STATIC_TABLE.size();
			if (id >= super.size) throw new H2Exception(ERROR_COMPRESS, "HPACK.Table.IndexError");
			return (Field) array[(id+head) % array.length];
		}

		public void _add(Field f) {
			int len = f.len();
			if (len > cap) {
				clear();
				return;
			}

			while (cap - size < len) _remove();
			ringAddFirst(f);

			if (owner != null) {
				f.id = STATIC_TABLE.size()+super.size;
				owner.encode_fp.add(f);

				if (f.v.length() > 0) {
					Field f1 = F(f.k.toString(), "");
					f1.id = f.id;
					owner.encode_fp.add(f1);
				}
			}
		}

		public void _remove() {
			Field f = removeLast();
			size -= f.len();

			if (owner != null) {
				owner.encode_fp.remove(f);
				f.v = "";
				owner.encode_fp.remove(f);
			}
		}

		@Override
		public void clear() {
			super.clear();
			size = 0;

			if (owner != null)
				owner.encode_fp.clear();
		}

		@Override
		public void setCapacity(int capacity) {
			if (cap == capacity) return;

			cap = capacity;
			if (capacity == 0) {
				clear();
			} else {
				while (size > capacity) _remove();
			}

			int max = capacity / 32;
			if ((capacity & 31) != 0) max++;

			super.setCapacity(max);
		}
	}

	private static final List<Field> STATIC_TABLE = Arrays.asList(F(":authority"), F(":method", "GET"), F(":method", "POST"), F(":path", "/"),
		F(":path", "/index.html"), F(":scheme", "http"), F(":scheme", "https"), F(":status", "200"), F(":status", "204"), F(":status", "206"),
		F(":status", "304"), F(":status", "400"), F(":status", "404"), F(":status", "500"), F("accept-charset"), F("accept-encoding", "gzip, deflate"),
		F("accept-language"), F("accept-ranges"), F("accept"), F("access-control-allow-origin"), F("age"), F("allow"),
		F("authorization"), F("cache-control"), F("content-disposition"), F("content-encoding"), F("content-language"),
		F("content-length"), F("content-location"), F("content-range"), F("content-type"), F("cookie"), F("date"), F("etag"),
		F("expect"), F("expires"), F("from"), F("host"), F("if-match"), F("if-modified-since"), F("if-none-match"), F("if-range"),
		F("if-unmodified-since"), F("last-modified"), F("link"), F("location"), F("max-forwards"), F("proxy-authenticate"),
		F("proxy-authorization"), F("range"), F("referer"), F("refresh"), F("retry-after"), F("server"), F("set-cookie"),
		F("strict-transport-security"), F("transfer-encoding"), F("user-agent"), F("vary"), F("via"), F("www-authenticate"));
	private static final MyHashSet<Field> STATIC_MAP = new MyHashSet<>();
	static {
		for (int i = 0; i < STATIC_TABLE.size();) {
			Field f = STATIC_TABLE.get(i);
			f.id = ++i;
			STATIC_MAP.add(f);
			if (f.v.length() > 0) {
				Field f1 = F(f.k.toString());
				f1.id = i;
				STATIC_MAP.add(f1);
			}
		}
	}

	private final BitBuffer bu;

	private boolean encoderSizeChanged, decoderSizeChanged;
	private final Table encode_tbl, decode_tbl;
	private final Field checker;
	private final MyHashSet<Field> encode_fp;

	public HPACK() {
		bu = new BitBuffer();

		encode_tbl = new Table(4096);
		encode_tbl.owner = this;
		checker = new Field();
		encode_fp = new MyHashSet<>();

		decode_tbl = new Table(4096);
	}

	public void setEncoderTableSize(int encodeMax/*REMOTE*/) {
		if (encodeMax != encode_tbl.cap) {
			encode_tbl.setCapacity(encodeMax);
			encoderSizeChanged = true;
		}
	}
	public void setDecoderTableSize(int decodeMax/*LOCAL*/) {
		if (decodeMax != decode_tbl.cap) {
			decode_tbl.setCapacity(decodeMax);
			decoderSizeChanged = true;
		}
	}

	public void encode(CharSequence k, CharSequence v, DynByteBuf out) {encode(k,v,out,0);}
	public void encode(CharSequence k, CharSequence v, DynByteBuf out, int indexType) {
		if (encoderSizeChanged) {
			writeInt(0x20, 5, encode_tbl.cap, out);
			encoderSizeChanged = false;
		}

		int id = findExistField(STATIC_MAP, k, v);
		// 0: not, <0: pair, >0: key
		if (id >= 0 && encode_tbl.cap > 0) {
			int dyn_id = findExistField(encode_fp, k, v);
			if (dyn_id < 0 || id == 0) id = dyn_id;
		}

		// 1xxx xxxx
		if (id < 0) {writeInt(0x80, 7, -id, out);return;}

		switch (indexType) {
			// 01xx xxxx
			case H2Connection.FIELD_SAVE -> {
				encode_tbl._add(F(k.toString(), v.toString()));
				writeInt(0x40, 6, id, out);
			}
			// 0000 xxxx
			case H2Connection.FIELD_DISCARD -> writeInt(0x00, 4, id, out);
			// 0001 xxxx
			case H2Connection.FIELD_DISCARD_ALWAYS -> writeInt(0x10, 4, id, out);
		}

		if (id == 0) writeString(k, out);
		writeString(v, out);
	}

	@Nullable
	public Field decode(DynByteBuf in) throws H2Exception {
		Field f = null;
		int k = in.readUnsignedByte();
		switch (k >>> 4) {
			// 1xxx xxxx    Indexed Header Field
			case 8, 9, 10, 11, 12, 13, 14, 15 -> {
				if ((k &= 0x7F) == 0) throw new H2Exception(ERROR_COMPRESS, "HPACK.Table.IndexError");
				f = decode_tbl.getField(k == 0x7F ? readInt(in, 0x7F) : k);
			}
			// 01xx xxxx    Literal Header Field with Incremental Indexing
			case 4, 5, 6, 7 -> decode_tbl._add(f = getField(in, k, 63));
			// 001x xxxx    Dynamic Table Size Update
			case 2, 3 -> {
				if ((k &= 31) == 31) k = readInt(in, 31);
				if (k < 0 || k > decode_tbl.cap) throw new H2Exception(ERROR_COMPRESS, "HPACK.Table.SizeError");
				decode_tbl.setCapacity(k);
				decoderSizeChanged = false;
			}
			// 0001 xxxx    Literal Header Field Never Indexed
			case 1 -> {
				f = getField(in, k, 15);
				f.id = -1; // it is not indexed
			}
			// 0000 xxxx    Literal Header Field without Indexing
			case 0 -> f = getField(in, k, 15);
		}
		if (decoderSizeChanged) throw new H2Exception(ERROR_COMPRESS, "HPACK.Table.UpdateExcepting");
		return f;
	}

	private int findExistField(MyHashSet<Field> set, CharSequence k, CharSequence v) {
		Field test = checker;

		test.k = k;
		test.v = v;
		Field in_table = set.find(test);
		if (in_table != test) {
			return -in_table.id;
		}

		test.v = "";
		in_table = set.find(test);
		if (in_table != test) {
			return in_table.id;
		}

		return 0;
	}

	private void writeString(CharSequence str, DynByteBuf out) {
		int len = huffmanLength(str);
		if (len < str.length()) {
			writeInt(128, 7, len, out);
			out.ensureWritable(len);
			huffmanEncode(str, bu.init(out));
		} else {
			writeInt(0, 7, str.length(), out);
			out.putAscii(str);
		}
	}

	private void writeInt(int prefix, int prefixLen, int val, DynByteBuf out) {
		int max = (1<<prefixLen)-1;
		if (val < max) {
			out.put((byte) (prefix | val));
		} else {
			out.put((byte) (prefix | max))
			   // includes zero
			   .putVarInt(val-max);
		}
	}

	private Field getField(DynByteBuf in, int k, int mask) throws H2Exception {
		k &= mask;
		return F(k!=0 ? decode_tbl.getField(k==mask ? readInt(in, mask) : k).k.toString() : checkKey(readString(in)), readString(in));
	}

	private static String checkKey(String key) throws H2Exception {
		int pseudo = 0;
		int i = 0;
		if (key.charAt(i) == ':') {i++;pseudo = ':';}
		while (i < key.length()) {
			var c = key.charAt(i++);
			if (c <= 0x20 || (c >= 0x41 && c <= 0x5a) || c >= 0x7f || c == pseudo)
				throw new H2Exception(ERROR_PROTOCOL, "Header.KeyError");
		}
		return null;
	}

	private String readString(DynByteBuf in) throws H2Exception {
		int first = in.readByte();
		int length = (first&0x7F)==0x7F?readInt(in, 0x7F):first&0x7F;
		if (first < 0) {
			int lim = in.wIndex();
			in.wIndex(in.rIndex+length);
			try {
				return huffmanDecode(bu.init(in));
			} finally {
				in.wIndex(lim);
			}
		}
		return in.readAscii(length);
	}

	private static int readInt(DynByteBuf in, int first) throws H2Exception {
		int shl = 0;
		while (in.isReadable()) {
			int b = in.readByte();

			first += (b & 0x7F) << shl;
			if ((b & 128) == 0) return first;

			if (shl >= 28) {
				throw new H2Exception(ERROR_COMPRESS, "HPACK.Varint.Overflow");
			}
			shl += 7;
		}

		throw new H2Exception(ERROR_COMPRESS, "HPACK.Varint.Malformed");
	}

	// region huffman

	private static final class Entry {
		Entry() {
			entries = new Entry[256];
			bit = 8;
		}

		Entry(int sym, int bit) {
			this.sym = (char) sym;
			this.bit = (byte) bit;
		}

		byte bit;
		char sym;

		Entry[] entries;

		Entry put(int id) {
			Entry e = entries[id];
			if (e == null) entries[id] = e = new Entry();
			return e;
		}

		Entry get(int id) {
			return entries[id&0xFF];
		}
	}
	private static final int[] HUFFMAN_SYM = new int[257];
	private static final byte[] HUFFMAN_SYM_LEN = new byte[257];
	private static final Entry HUFFMAN_TAB = new Entry();
	static {
		String table = "b/4v//9jn///xc///+Pn///yc///+Xn///zc///+fn///0Y///q9////5z///6ef///V7////3n/" +
			"//18///+zn///28///+7n///38////Dn///48////L3////3P///z5///+nP///15///+3P///35////HP///55" +
			"////XP///7Mor+Ffyz/pv/kyqPhf+lf0r+0fK/9o+jLGXMwUBQlEZkzRmzOGdM8Z89xH2//+GgZ/2r/G/+jQnun" +
			"vHvnwHwnxHxnyHynzHzn0H0n1H1n2H2n3H3n4H4n5I/D80frf/c//+Df/jv/w0T//6UZoykNIUppTTGnKY/Q/U1" +
			"BqTVFOas/Y1hUFSa0/c/g/k/o/t//+X/x3/63/7z////Kf/81v//Sp//z0//6Lf/+nb//1Lf/+rf//7Nv//Wv//" +
			"9q///27///cv//92///3sf//13//+/j//+zH//9tv//Xv//+DH//91///w3///Ff//8d///yV//7lv//Yv//+W3" +
			"//s3///Nf//8+P//77f/+1X//u0//6bf/+3b//3L///ov//+mv//ev//+q3//u2//97H//+Ff/+/b//37///rv/" +
			"/+yv//gr//4bf//BX//xX///bb//4b///uv//++n//Vb//4rf//Hb//5L///wt//8tv//mv///HX///Br///hp/" +
			"/1z//42//+e///8rf//Rn///Zr///i1///x6///5N///97f///f1///y4///xz///tn//yr//49f//82////Bv/" +
			"//w6///59///+LH//+Vf//JX//y6///6Nf//9PP///93///49///+Tf///lp//2Y///zp//21//81v//pr//56/" +
			"/+i///87f//Vb//68///7s///78f//6Y///11///1X///pr///r3///5tf//9mv//+3f///n3///6N///+nf///" +
			"q3///6+f///9v///2b///9u////dv///37///+Gv//+73////4";
		ByteList list = ByteList.allocate(999);
		Base64.decode(table, list);

		BitBuffer br = new BitBuffer(list);
		for (int i = 0; i < 257; i++) {
			int len = br.readBit(5);
			int code = br.readBit(len);

			HUFFMAN_SYM[i] = code;
			HUFFMAN_SYM_LEN[i] = (byte) len;

			Entry entry = HUFFMAN_TAB;
			while (len > 8) {
				len -= 8;
				entry = entry.put(0xFF & (code>>>len));
			}

			Entry end = new Entry(i, len);

			// etc 0b1110[nnnn]
			int shift = 8 - len;
			int high = (code << shift) & 255;
			int count = 1 << shift;

			// "模糊匹配"
			while (count-- > 0) {
				entry.entries[high++] = end;
			}
		}
	}

	private static void huffmanEncode(CharSequence seq, BitBuffer out) {
		for (int i = 0; i < seq.length(); i++) {
			int id = seq.charAt(i);
			out.writeBit(HUFFMAN_SYM_LEN[id], HUFFMAN_SYM[id]);
		}
		if (out.bitPos > 0) {
			long l = out.ob << (8 - out.bitPos);
			l |= (0xFF >>> out.bitPos);
			out.list.put((byte) l);
			out.bitPos = 0;
		}
	}
	private static int huffmanLength(CharSequence seq) {
		long len = 0;
		for (int i = 0; i < seq.length(); i++) {
			len += HUFFMAN_SYM_LEN[seq.charAt(i)];
		}
		if ((len & 7) != 0) len += 8;
		return (int) (len >>> 3);
	}

	private static String huffmanDecode(BitBuffer in) throws H2Exception {
		CharList tmp = IOUtil.getSharedCharBuf();

		out:
		while (in.list.isReadable()) {
			Entry table = HUFFMAN_TAB;
			while (true) {
				int remain = in.readableBits();

				table = table.get(in.readBit(8));
				if (table == null) throw new H2Exception(ERROR_COMPRESS, "HPACK.Huffman.InvalidCode");

				if (remain < table.bit) break out;
				if (table.entries == null) {
					if (table.sym == 256) throw new H2Exception(ERROR_COMPRESS, "HPACK.Huffman.UnexpectedEOF");
					tmp.append(table.sym);
					in.retractBits(8-table.bit);
					break;
				}
			}
		}
		in.retractBits(-in.readableBits());

		int mask = (1 << in.bitPos) - 1;
		if (in.bitPos >0 && (in.list.get(in.list.rIndex) & mask) != mask) {
			throw new H2Exception(ERROR_COMPRESS, "HPACK.Huffman.InvalidPadding");
		}
		in.endBitRead();

		return tmp.toString();
	}

	// endregion
}