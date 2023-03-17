package roj.config.word;

/**
 * @author Roj234
 * @since 2021/5/3 22:35
 */
public class Word_D extends Word {
	public double number;

	public Word_D(short type, int index, double i, String val) {
		init(type, index, val);
		this.number = i;
	}

	@Override
	public int asInt() {
		return (int) number;
	}
	@Override
	public double asDouble() {
		return number;
	}
	@Override
	public long asLong() {
		return (long) number;
	}

	@Override
	public Word copy() {
		return new Word_D(type, index, number, val);
	}
}
