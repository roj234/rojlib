package roj.compiler.diagnostic;

import org.jetbrains.annotations.Nls;
import roj.asm.tree.IClass;
import roj.compiler.JavaLexer;
import roj.compiler.context.CompileUnit;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Arrays;
import java.util.Locale;

/**
 * @author Roj234
 * @since 2024/2/7 0007 4:53
 */
public class Diagnostic {
	private final IClass source;
	private final Kind kind;
	private final String code;
	private final Object[] args;
	private final int wordStart, wordEnd;

	private int lineNumber, columnNumber;
	private String line;

	public Diagnostic(IClass source, Kind kind, int wordStart, int wordEnd, String code, Object[] args) {
		this.source = source;
		this.kind = kind;
		this.code = code;
		this.args = args;
		this.wordStart = wordStart;
		this.wordEnd = wordEnd;
	}

	public Kind getKind() { return kind; }

	public String getFilePath() {return source instanceof CompileUnit cu ? cu.getFilePath() : source.name();}
	public IClass getSource() { return source; }
	public int getStartPosition() { return wordStart; }
	public int getEndPosition() { return wordEnd; }

	public String getCode() { return code; }
	public Object[] getArgs() { return args; }

	public int getLineNumber() { initLines(); return lineNumber; }
	public int getColumnNumber() { initLines(); return columnNumber; }
	public String getLine() { initLines(); return line; }

	private void initLines() {
		if (line != null || !(source instanceof CompileUnit cu)) return;

		CharSequence lines = cu.getLexer().getText();
		int pos = wordStart;
		if (pos < 0) {
			lineNumber = -1;
			columnNumber = -1;
			return;
		}

		int ln = 1;
		int i = 0;
		while (true) {
			int j = TextUtil.gNextCRLF(lines, i);
			if (j > pos || j < 0) {
				CharList sb = new CharList().append(lines, i, j < 0 ? lines.length() : j).trimLast();
				lineNumber = ln;
				columnNumber = pos-i;
				line = sb.toStringAndFree();
				break;
			}

			i = j;
			ln++;
		}
	}

	@Nls
	public String getMessage(Locale locale) {
		if (!locale.equals(Locale.SIMPLIFIED_CHINESE)) return code;
		return JavaLexer.translate.translate(code+(args == null ? "" : ":"+(TextUtil.join(Arrays.asList(args), ":"))));
	}
}