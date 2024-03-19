package roj.crypt.asn1;

import roj.collect.SimpleList;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.BitBuffer;
import roj.util.ByteList;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/3/22 0022 21:05
 */
public abstract class DerValue {
	public byte flags;

	public static final int COMPOSITE = 32;
	public static final int INTEGER = 2, BIT_STRING = 3, OCTET_STRING = 4, NULL = 5, OID = 6, UTF8_STRING = 12, SEQUENCE = 16|COMPOSITE, SET = 17|COMPOSITE, PrintableString = 19, IA5String = 22, UTCTime = 23, GeneralizedTime = 24;
	public abstract int type();

	public List<DerValue> collection() { throw new UnsupportedOperationException(); }
	public byte[] unparsedData() { throw new UnsupportedOperationException(); }

	public static final class Int extends DerValue {
		public BigInteger integer;
		public Int(BigInteger integer) {this.integer = integer;}
		public int type() { return INTEGER; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Int anInt)) return false;
			return integer.equals(anInt.integer);
		}
		@Override
		public int hashCode() { return integer.hashCode(); }
		@Override
		public String toString() { return "INTEGER["+integer+"]"; }
	}
	public static final class Bit extends DerValue {
		public byte trash;
		public byte[] data;
		public Bit(int trash, byte[] data) {this.trash = (byte) trash; this.data = data;}
		public int type() { return BIT_STRING; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Bit bit)) return false;

			if (trash != bit.trash) return false;
			return Arrays.equals(data, bit.data);
		}
		@Override
		public int hashCode() {
			int result = trash;
			result = 31 * result + Arrays.hashCode(data);
			return result;
		}
		@Override
		public String toString() {
			BitBuffer buf = new BitBuffer(new ByteList(data));
			int count = data.length*8 - trash;
			CharList sb = new CharList().append("BIT(").append(count).append(")[");
			int cnt = Math.min(count, 100);
			for (int i = 0; i < cnt; i++) sb.append((char)('0'+buf.readBit1()));
			if (cnt < count) sb.append("...");
			return sb.append(']').toStringAndFree();
		}
	}
	public static final class Bytes extends DerValue {
		public byte[] data;
		public Bytes(byte[] data) {this.data = data;}
		public int type() { return OCTET_STRING; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Bytes bytes)) return false;
			return Arrays.equals(data, bytes.data);
		}
		@Override
		public int hashCode() { return Arrays.hashCode(data); }
		@Override
		public String toString() { return "OCTET["+TextUtil.bytes2hex(data)+"]"; }
	}
	public static final class Null extends DerValue {
		public static final Null NUL = new Null();
		public int type() { return NULL; }

		@Override
		public String toString() { return "NULL"; }
	}
	public static class OID extends DerValue {
		public int[] oid;
		public OID(int[] oid) { this.oid = oid; }
		public OID(String s) {
			List<String> split = TextUtil.split(new SimpleList<>(), s, '.');
			oid = new int[split.size()];
			for (int i = 0; i < split.size(); i++) {
				oid[i] = Integer.parseInt(split.get(i));
			}
		}
		public int type() { return OID; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof OID oid)) return false;
			return Arrays.equals(this.oid, oid.oid);
		}
		@Override
		public int hashCode() { return Arrays.hashCode(oid); }
		@Override
		public String toString() {
			CharList sb = new CharList().append("OID[");
			int i = 0;
			while (true) {
				sb.append(oid[i]);
				if (++i == oid.length) return sb.append(']').toStringAndFree();
				sb.append('.');
			}
		}
	}
	public static final class Str extends DerValue {
		public String text;
		public Str(int type, String text) { this.flags = (byte) type; this.text = text; }
		public int type() { return flags&31; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Str str)) return false;
			return flags == str.flags && text.equals(str.text);
		}
		@Override
		public int hashCode() { return flags + 31*text.hashCode(); }

		@Override
		public String toString() {
			return new CharList().append(switch (flags&31) {
				default -> "UNKNOWN";
				case UTF8_STRING -> "UTF8";
				case PrintableString -> "PrintableString";
				case IA5String -> "IA5String";
				case UTCTime -> "UTCTime";
				case GeneralizedTime -> "GeneralizedTime";
			}).append('[').append(text).append(']').toStringAndFree();
		}
	}
	public static final class Coll extends DerValue {
		public String name;
		public String[] names;
		public List<DerValue> list;
		public Coll(int type, List<DerValue> list) { this.flags = (byte) type; this.list = list; }

		public Coll(int type, String name, String[] names, SimpleList<DerValue> list) {
			this.flags = (byte) type;
			this.name = name;
			this.names = names;
			this.list = list;
		}

		public int type() { return flags&31; }
		@Override
		public List<DerValue> collection() { return list; }
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Coll coll)) return false;
			return flags == coll.flags && list.equals(coll.list);
		}
		@Override
		public int hashCode() { return flags + list.hashCode(); }
		@Override
		public String toString() {
			if (names == null) return (((flags&31) == SEQUENCE)?"SEQUENCE OF":"SET OF")+list;
			else {
				CharList sb = new CharList();
				sb.append(name).append('{');
				for (int i = 0; i < list.size(); i++) {
					sb.append("\n").append(names[i]).append(" => ").append(list.get(i)).append(", ");
				}
				return sb.append("\n}").toStringAndFree();
			}
		}
	}
	public static final class Unk extends DerValue {
		public byte[] data;
		public Unk(int type, byte[] data) { this.flags = (byte) type; this.data = data; }
		public int type() { return flags; }
		@Override
		public byte[] unparsedData() { return data; }
		@Override
		public String toString() {
			CharList sb = new CharList().append("Unparseable[").append(flags&31);
			if ((flags&32) != 0) sb.append(",composite");
			sb.append(switch (flags >>> 6) {
				default -> "";
				case 1 -> ",application";
				case 2 -> ",context";
				case 3 -> ",private";
			});
			return sb.append(",length=").append(data.length).append(']').toStringAndFree();
		}
	}
	public static final class ParsedTime extends DerValue {
		public long timestamp;
		public ParsedTime(int type, long time) { this.flags = (byte) type; this.timestamp = time; }
		public int type() { return flags&31; }
	}
}