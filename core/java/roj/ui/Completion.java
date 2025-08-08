package roj.ui;

/**
 * @author Roj234
 * @since 2023/11/20 2:14
 */
public final class Completion {
	public Text description, completion;
	public int offset;
	public boolean isTip;

	public Completion(String str) { completion = new Text(str).color16(Tty.BLACK+Tty.HIGHLIGHT); }
	public Completion(Text str) { completion = str; }
	public Completion(Text str, Text desc) {
		completion = str;
		description = desc;
	}
	public Completion(Text str, Text desc, int off) {
		if (off > 0) throw new IllegalArgumentException("off must <= 0: "+off);
		completion = str;
		description = desc;
		offset = off;
	}
}