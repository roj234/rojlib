package roj.config.word;

/**
 * @author Roj234
 * @since 2020/10/3 19:20
 */
public class Word {
	public static final short
		LITERAL = 0,   // 变量名
		CHARACTER = 1, // 字符
		STRING = 2,    // 字符串

		INTEGER = 3,   // int32
		DOUBLE = 4,    // float64
		FLOAT = 5,     // float32
		LONG = 6,      // int64

	    EOF = -1,      // 文件结束

	RFCDATE_DATE = 7, RFCDATE_DATETIME = 8, RFCDATE_DATETIME_TZ = 9;

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
	public String toString() {
		return "Token{#" + type + "@'" + val + '\'' + '}';
	}

	public short type() { return type; }
	public int pos() { return pos; }
	public String val() { return val; }

	public int asInt() { throw new UnsupportedOperationException(this + "is not number"); }
	public long asLong() { throw new UnsupportedOperationException(this + "is not number"); }
	public float asFloat() { throw new UnsupportedOperationException(this + "is not number"); }
	public double asDouble() { throw new UnsupportedOperationException(this + "is not number"); }

	public Word copy() { return new Word().init(type, pos, val); }

	/**
	 * @since 2024/2/17 18:05
	 */
	public static final class F extends Word {
		private final float num;
		public F(int index, float i, String val) {
			this.type = FLOAT;
			this.pos = index;
			this.val = val;
			this.num = i;
		}

		@Override
		public float asFloat() { return num; }
		@Override
		public double asDouble() { return num; }

		@Override
		public Word init(int type, int index, String word) { throw new UnsupportedOperationException("数字word不可变"); }
		@Override
		public Word copy() { return this; }
	}

	/**
	 * @since 2021/5/3 22:35
	 */
	public static final class D extends Word {
		private final double i;
		public D(int pos, double i, String val) {
			this.type = DOUBLE;
			this.pos = pos;
			this.val = val;
			this.i = i;
		}

		@Override
		public double asDouble() { return i; }

		@Override
		public Word init(int type, int index, String word) { throw new UnsupportedOperationException("数字word不可变"); }
		@Override
		public Word copy() { return this; }
	}
	public static final class I extends Word {
		private final int i;

		public I(int pos, int i, String val) {
			this.type = INTEGER;
			this.pos = pos;
			this.val = val;
			this.i = i;
		}

		@Override
		public int asInt() { return i; }
		@Override
		public long asLong() { return i; }
		@Override
		public double asDouble() { return i; }

		@Override
		public Word init(int type, int index, String word) { throw new UnsupportedOperationException("数字word不可变"); }
		@Override
		public Word copy() { return this; }
	}
	public static final class L extends Word {
		private final long i;

		public L(int type, int pos, long i, String val) {
			this.type = (short) type;
			this.pos = pos;
			this.val = val;
			this.i = i;
		}
		public L(int pos, long i, String val) {
			this.type = LONG;
			this.pos = pos;
			this.val = val;
			this.i = i;
		}

		@Override
		public double asDouble() { return i; }
		@Override
		public long asLong() { return i; }

		@Override
		public Word init(int type, int index, String word) { throw new UnsupportedOperationException("数字word不可变"); }
		@Override
		public Word copy() { return this; }
	}
}