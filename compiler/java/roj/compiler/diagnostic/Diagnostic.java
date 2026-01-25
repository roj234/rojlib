package roj.compiler.diagnostic;

import org.jetbrains.annotations.Nls;
import roj.asm.ClassDefinition;
import roj.compiler.CompileUnit;
import roj.compiler.LavaTokenizer;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Locale;

/**
 * @author Roj234
 * @since 2024/2/7 4:53
 */
public class Diagnostic {
	private final ClassDefinition source;
	private final Kind kind;
	private final String code;
	private final IText message;
	private final int offset, length;

	private int lineNumber, columnNumber;
	private String line;

	public Diagnostic(ClassDefinition source, Kind kind, int wordStart, int wordEnd, String code, IText message) {
		this.source = source;
		this.kind = kind;
		this.code = code;
		this.message = message;

		this.offset = wordStart;
		this.length = wordEnd-wordStart;
	}

	public Kind getKind() { return kind; }

	public String getFile() {return source == null ? LavaTokenizer.i18n.translate("lava.compiler") : source instanceof CompileUnit cu ? cu.getSourceFile() : source.name();}
	public ClassDefinition getSource() { return source; }
	public int getStartPosition() { return offset; }
	public int getEndPosition() { return offset+length; }

	public String getCode() {return code;}
	public IText getMessage() {return message;}

	public int getLineNumber() { initLines(); return lineNumber; }
	public int getColumnNumber() { initLines(); return columnNumber; }
	public int getLength() { return length; }
	public String getLine() { initLines(); return line; }

	private void initLines() {
		if (line != null || !(source instanceof CompileUnit cu)) return;

		CharSequence lines = cu.getCode();
		int pos = offset;
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
				CharList sb = new CharList().append(lines, i, j < 0 ? lines.length() : j).rtrim();
				lineNumber = ln;
				columnNumber = MathUtils.clamp(pos-i, 0, sb.length());
				line = sb.toStringAndFree();
				break;
			}

			i = j;
			ln++;
		}
	}

	@Nls
	public String getMessage(Locale locale) {
		CharList buf = new CharList();
		message.appendTo(buf, LavaTokenizer.i18n);
		return buf.toStringAndFree();
	}
}