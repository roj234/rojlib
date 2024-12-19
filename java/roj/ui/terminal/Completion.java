package roj.ui.terminal;

import roj.ui.AnsiString;
import roj.ui.Terminal;

/**
 * @author Roj234
 * @since 2023/11/20 0020 2:14
 */
public final class Completion {
	public AnsiString description, completion;
	public int offset;
	public boolean isTip;

	public Completion(String str) { completion = new AnsiString(str).color16(Terminal.BLACK+Terminal.HIGHLIGHT); }
	public Completion(AnsiString str) { completion = str; }
	public Completion(AnsiString str, AnsiString desc) {
		completion = str;
		description = desc;
	}
	public Completion(AnsiString str, AnsiString desc, int off) {
		if (off > 0) throw new IllegalArgumentException("off must <= 0: "+off);
		completion = str;
		description = desc;
		offset = off;
	}
}