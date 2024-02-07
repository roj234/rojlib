package roj.ui.terminal;

import roj.ui.AnsiString;
import roj.ui.CLIUtil;

/**
 * @author Roj234
 * @since 2023/11/20 0020 2:14
 */
public final class Completion {
	public AnsiString description, completion;
	public boolean replaceBefore;

	public Completion(String str) { completion = new AnsiString(str).bgColorRGB(0xFFFFF).color16(CLIUtil.BLACK+CLIUtil.HIGHLIGHT); }
	public Completion(AnsiString str) { completion = str; }
	public Completion(AnsiString str, AnsiString desc) {
		completion = str;
		description = desc;
	}
}