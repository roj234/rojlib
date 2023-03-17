package roj.config.word;

/**
 * @author Roj234
 * @since 2021/5/3 22:35
 */
public class Word_L extends Word {
	public long number;

	public Word_L(int type, int index, long i, String val) {
		init(type, index, val);
		this.number = i;
	}
	public Word_L(int index, long i, String val) {
		init(Word.LONG, index, val);
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
		return number;
	}

	@Override
	public Word copy() {
		return new Word_L(type, index, number, val);
	}
}
