package roj.config;

import org.jetbrains.annotations.Contract;

/**
 * @author Roj234
 * @since 2020/10/3 19:20
 */
public class Word {
	public static final short
		EOF = -1,
		LITERAL = 0, STRING = 1,
		INTEGER = 2, LONG = 3, FLOAT = 4, DOUBLE = 5,
		RFCDATE_DATE = 6, RFCDATE_DATETIME = 7, RFCDATE_DATETIME_TZ = 8;

	short type;
	int pos;
	String val;

	public Word() {}

	/**
	 * 复用对象
	 */
	public Word init(int type, int index, String word) {
		this.type = (short) type;
		this.pos = index;
		this.val = word;
		return this;
	}

	@Override
	public String toString() { return "Token{#"+type+"@'"+val+'\''+'}'; }

	@Contract(pure = true)
	public short type() { return type; }
	@Contract(pure = true)
	public int pos() { return pos; }
	@Contract(pure = true)
	public String val() { return val; }

	@Contract(pure = true)
	public int asInt() { throw new UnsupportedOperationException(this+"不是数字"); }
	@Contract(pure = true)
	public long asLong() { throw new UnsupportedOperationException(this+"不是数字"); }
	@Contract(pure = true)
	public float asFloat() { throw new UnsupportedOperationException(this+"不是数字"); }
	@Contract(pure = true)
	public double asDouble() { throw new UnsupportedOperationException(this+"不是数字"); }

	public Word copy() { return new Word().init(type, pos, val); }

	public static Word timeWord(int type, int pos, long value, String represent) { return new L(type, pos, value, represent); }
	public static Word numberWord(int pos, int value, String represent) { return new I(pos, value, represent); }
	public static Word numberWord(int pos, long value, String represent) { return new L(pos, value, represent); }
	public static Word numberWord(int pos, float value, String represent) { return new F(pos, value, represent); }
	public static Word numberWord(int pos, double value, String represent) { return new D(pos, value, represent); }
	private static class Num extends Word {
		public final Word init(int type, int index, String word) { throw new UnsupportedOperationException("数字word不可变"); }
		public final Word copy() { return this; }
	}
	private static final class I extends Num {
		private final int i;
		I(int pos, int i, String val) {
			this.type = INTEGER;
			this.pos = pos;
			this.val = val;
			this.i = i;
		}
		public int asInt() { return i; }
		public long asLong() { return i; }
		public double asDouble() { return i; }
	}
	private static final class L extends Num {
		private final long i;
		L(int type, int pos, long i, String val) {
			this.type = (short) type;
			this.pos = pos;
			this.val = val;
			this.i = i;
		}
		L(int pos, long i, String val) {
			this.type = LONG;
			this.pos = pos;
			this.val = val;
			this.i = i;
		}
		public long asLong() { return i; }
		public double asDouble() { return i; }
	}
	private static final class F extends Num {
		private final float num;
		F(int index, float i, String val) {
			this.type = FLOAT;
			this.pos = index;
			this.val = val;
			this.num = i;
		}
		public float asFloat() { return num; }
		public double asDouble() { return num; }
	}
	private static final class D extends Num {
		private final double i;
		D(int pos, double i, String val) {
			this.type = DOUBLE;
			this.pos = pos;
			this.val = val;
			this.i = i;
		}
		public double asDouble() { return i; }
	}
}