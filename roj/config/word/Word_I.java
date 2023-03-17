package roj.config.word;

/**
 * @author Roj234
 * @since 2021/5/3 22:35
 */
public class Word_I extends Word {
	public int number;

	public Word_I(int index, int i, String val) {
		init(Word.INTEGER, index, val);
		this.number = i;
	}

	@Override
	public int asInt() {
		return number;
	}
	@Override
	public double asDouble() {
		return number;
	}
	@Override
	public long asLong() {
		return number;
	}

	@Override
	public Word copy() {
		return new Word_I(index, number, val);
	}
}
