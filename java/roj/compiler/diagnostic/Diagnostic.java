package roj.compiler.diagnostic;

import org.jetbrains.annotations.Nls;
import roj.compiler.JavaLexer;
import roj.compiler.context.CompileUnit;
import roj.text.TextUtil;

import java.util.Arrays;
import java.util.Locale;

/**
 * @author Roj234
 * @since 2024/2/7 0007 4:53
 */
public class Diagnostic {
	private final CompileUnit source;
	private final Kind kind;
	private final String code;
	private final Object[] args;
	private int wordStart, wordEnd, lineNumber, columnNumber;

	public Diagnostic(CompileUnit source, Kind kind, String code, Object[] args, int wordStart, int wordEnd) {
		this.source = source;
		this.kind = kind;
		this.code = code;
		this.args = args;
		this.wordStart = wordStart;
		this.wordEnd = wordEnd;
		// 这两个get的时候再计算，或者能给也好
	}
	public Diagnostic(CompileUnit source, Kind kind, String code, Object[] args, int wordStart, int wordEnd, int lineNumber, int columnNumber) {
		this.source = source;
		this.kind = kind;
		this.code = code;
		this.args = args;
		this.wordStart = wordStart;
		this.wordEnd = wordEnd;
		this.lineNumber = lineNumber;
		this.columnNumber = columnNumber;
	}

	public Kind getKind() { return kind; }
	public CompileUnit getSource() { return source; }
	public long getPosition() { return wordEnd; }
	public long getStartPosition() { return wordStart; }
	public long getEndPosition() { return wordEnd; }
	public long getLineNumber() { return lineNumber; }
	public long getColumnNumber() { return columnNumber; }
	public String getCode() { return code; }
	public Object[] getArgs() { return args; }

	@Nls
	public String getMessage(Locale locale) {
		if (locale.equals(Locale.SIMPLIFIED_CHINESE)) {
			return JavaLexer.translate.translate(code+TextUtil.join(Arrays.asList(args), ":"));
		} else {
			return code;
		}
	}
}