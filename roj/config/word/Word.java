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
	int index;
	String val;

	public Word() {}

	/**
	 * 复用对象
	 */
	public Word init(int type, int index, String word) {
		this.type = (short) type;
		this.index = index;
		this.val = word;
		return this;
	}

	public Word(int index) {
		this.type = EOF;
		this.index = index;
		this.val = "/EOF";
	}

	@Override
	public String toString() {
		return "Token{#" + type + "@'" + val + '\'' + '}';
	}

	public short type() {
		return type;
	}
	public int pos() {
		return index;
	}
	public String val() {
		return val;
	}

	public int asInt() {
		throw new UnsupportedOperationException(this + "is not number");
	}
	public double asDouble() {
		throw new UnsupportedOperationException(this + "is not number");
	}
	public long asLong() {
		throw new UnsupportedOperationException(this + "is not number");
	}

	public Word copy() {
		return new Word().init(type, index, val);
	}
}
