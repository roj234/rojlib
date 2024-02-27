package roj.crypt.asn1;

import roj.collect.IntBiMap;
import roj.collect.ListMap;
import roj.collect.SimpleList;
import roj.config.data.*;
import roj.config.serial.CVisitor;
import roj.config.serial.ToJson;
import roj.text.CharList;
import roj.text.TextUtil;

import java.math.BigInteger;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/3/22 0022 21:05
 */
public interface DerValue {
	int COMPOSITE = 32;
	int INTEGER = 2, BIT_STRING = 3, OCTET_STRING = 4, NULL = 5, OID = 6, UTF8_STRING = 12, SEQUENCE = 16|COMPOSITE, SET = 17|COMPOSITE, PrintableString = 19, IA5String = 22, UTCTime = 23, GeneralizedTime = 24;

	static CEntry INTEGER(BigInteger bi) {return new Int(bi);}
	static CEntry CHOICE(int encType, CEntry ref) { return new Choice(encType, ref); }
	static CIntArray OID(String s) {
		List<String> split = TextUtil.split(new SimpleList<>(), s, '.');
		int [] oid = new int[split.size()];
		for (int i = 0; i < split.size(); i++) {
			oid[i] = Integer.parseInt(split.get(i));
		}
		return new CIntArray(oid);
	}

	final class Int extends CEntry {
		public BigInteger value;

		public Int(BigInteger data) {value = data;}

		@Override
		public Type getType() {return Type.INTEGER;}

		@Override
		public void accept(CVisitor ser) {ser.value("bigInt:"+value);}

		@Override
		public Object raw() {return value;}
		@Override
		protected CharList toJSON(CharList sb, int depth) {return sb.append(value);}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Int anInt)) return false;
			return value.equals(anInt.value);
		}
		@Override
		public int hashCode() {return value.hashCode();}
	}

	final class Bits extends CByteArray {
		public int bits;
		public Bits(int trashBits, byte[] data) {
			super(data);
			this.bits = (data.length << 3) - trashBits;
		}
	}

	final class Opaque extends CByteArray {
		public int type;

		public Opaque(int type, byte[] bytes) {
			super(bytes);
			this.type = type;
		}

		@Override
		public void accept(CVisitor ser) {ser.value("unknown("+type+"):"+TextUtil.bytes2hex(value));}
	}

	final class Sequence extends CMap {
		public final String type;

		public Sequence(String type, IntBiMap<String> index, List<CEntry> values) {
			super(new ListMap<>(index, values));
			this.type = type;
		}
	}

	final class Choice extends CEntry {
		public CEntry ref;
		public byte encType;

		public Choice(int encType, CEntry ref) {
			this.ref = ref;
			this.encType = (byte) encType;
		}

		public Type getType() {return ref.getType();}
		public boolean contentEquals(CEntry o) {return this == o || ref.contentEquals(o);}
		public boolean mayCastTo(Type o) { return ref.mayCastTo(o); }

		public boolean asBool() {return ref.asBool();}
		public int asInteger() {return ref.asInteger();}
		public long asLong() {return ref.asLong();}
		public float asFloat() {return ref.asFloat();}
		public double asDouble() {return ref.asDouble();}
		public String asString() {return ref.asString();}
		public CMap asMap() {return ref.asMap();}
		public CList asList() {return ref.asList();}

		@Override
		public void accept(CVisitor ser) {ref.accept(ser);}
		@Override
		public Object raw() {return ref.raw();}
		@Override
		public Object rawDeep() {return ref.rawDeep();}
		@Override
		protected CharList toJSON(CharList sb, int depth) {ToJson ser = new ToJson();accept(ser);return ser.getValue();}

		@Override
		public int hashCode() {return ref.hashCode();}
		@Override
		public boolean equals(Object obj) {return this == obj || ref.equals(obj);}
	}
}