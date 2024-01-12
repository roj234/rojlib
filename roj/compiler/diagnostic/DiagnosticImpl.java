package roj.compiler.diagnostic;

import org.jetbrains.annotations.Nls;
import roj.compiler.JavaLexer;
import roj.compiler.context.CompileUnit;
import roj.text.TextUtil;

import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.Locale;

/**
 * @author Roj234
 * @since 2024/2/7 0007 4:53
 */
public class DiagnosticImpl implements Diagnostic<CompileUnit> {
	private final CompileUnit source;
	private final Kind kind;
	private final String code;
	private final Object[] args;
	private final int wordStart, wordEnd, lineNumber, columnNumber;

	public DiagnosticImpl(CompileUnit source, Kind kind, String code, Object[] args, int wordStart, int wordEnd, int lineNumber, int columnNumber) {
		this.source = source;
		this.kind = kind;
		this.code = code;
		this.args = args;
		this.wordStart = wordStart;
		this.wordEnd = wordEnd;
		this.lineNumber = lineNumber;
		this.columnNumber = columnNumber;
	}

	@Override
	public Kind getKind() { return kind; }
	@Override
	public CompileUnit getSource() { return source; }
	@Override
	public long getPosition() { return wordEnd; }
	@Override
	public long getStartPosition() { return wordStart; }
	@Override
	public long getEndPosition() { return wordEnd; }
	@Override
	public long getLineNumber() { return lineNumber; }
	@Override
	public long getColumnNumber() { return columnNumber; }
	@Override
	public String getCode() { return code; }
	public Object[] getArgs() { return args; }

	@Nls
	@Override
	public String getMessage(Locale locale) {
		if (locale.equals(Locale.SIMPLIFIED_CHINESE)) {
			return JavaLexer.translate.translate(code+TextUtil.join(Arrays.asList(args), ":"));
		} else {
			return code;
		}
	}
}