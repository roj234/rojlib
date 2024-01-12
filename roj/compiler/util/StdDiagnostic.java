package roj.compiler.util;

import roj.compiler.context.CompileUnit;
import roj.math.MutableInt;
import roj.text.LineReader;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * @author solo6975
 * @since 2020/12/31 22:22
 */
public final class StdDiagnostic implements DiagnosticListener<CompileUnit> {
	int err, warn, op;

	public StdDiagnostic(int maxError, int maxWarn, int warnOps) {
		this.err = maxError;
		this.warn = maxWarn;
		this.op = warnOps;
	}

	private static String buildErrorMessage(Diagnostic<? extends CompileUnit> diagnostic, EnumMap<Diagnostic.Kind, MutableInt> map) {
		StringBuilder sb = new StringBuilder();
		if (diagnostic.getSource() != null) {
			String file = diagnostic.getSource().getFilePath();
			sb.append(file, 6, file.length()).append(':');
			if (diagnostic.getLineNumber() > 0) sb.append(diagnostic.getLineNumber()).append(':');
			sb.append(' ');
		}
		sb.append(getErrorMsg(diagnostic.getKind(), map)).append(':').append(' ').append(diagnostic.getMessage(Locale.CHINA)).append('\n');
		if (diagnostic.getLineNumber() > 0) {
			sb.append(getNearCode(diagnostic.getSource(), diagnostic.getLineNumber())).append('\n');
			for (int i = 0; i < diagnostic.getColumnNumber(); i++) {
				sb.append(' ');
			}
			sb.setCharAt(sb.length() - 1, '^');
			sb.append('\n');
		}

		return sb.toString();
	}

	private static String getNearCode(CompileUnit source, long lineNumber) {
		if (lineNumber == -1) return "";
		return LineReader.getLine(source.getContext(), (int) lineNumber - 1);
	}

	static final Function<Diagnostic.Kind, MutableInt> k2i = (kind1) -> new MutableInt(0);

	private static String getErrorMsg(Diagnostic.Kind kind, EnumMap<Diagnostic.Kind, MutableInt> kinds) {
		switch (kind) {
			case NOTE:
				return "注";
			case ERROR:
				if (kinds != null) kinds.computeIfAbsent(kind, k2i).increment();
				return "错误";
			case OTHER:
				return "其他";
			case WARNING:
				if (kinds != null) kinds.computeIfAbsent(kind, k2i).increment();
				return "警告";
			case MANDATORY_WARNING:
				if (kinds != null) kinds.computeIfAbsent(Diagnostic.Kind.WARNING, k2i).increment();
				return "强警告";
		}
		throw new IllegalArgumentException();
	}


	final EnumMap<Diagnostic.Kind, MutableInt> kinds = new EnumMap<>(Diagnostic.Kind.class);

	/**
	 * Invoked when a problem is found.
	 *
	 * @param diagnostic a diagnostic representing the problem that
	 * was found
	 *
	 * @throws NullPointerException if the diagnostic argument is
	 * {@code null} and the implementation cannot handle {@code null}
	 * arguments
	 */
	@Override
	public void report(Diagnostic<? extends CompileUnit> diagnostic) {
		switch (diagnostic.getKind()) {
			case WARNING:
			case MANDATORY_WARNING:
				switch (op) {
					case 1:
						System.err.println(buildErrorMessage(diagnostic, kinds));
						throw new RuntimeException("警告as错误");
					case 2:
						return;
				}
				if (warn > 0) {
					warn--;
				} else {
					return;
				}
				break;
			case ERROR:
				if (err > 0) {
					err--;
				} else {
					return;
				}
		}

		System.err.println(buildErrorMessage(diagnostic, kinds));
	}

	public void conclusion() {
		PrintStream errorOutput = System.err;
		for (Map.Entry<Diagnostic.Kind, MutableInt> entry : kinds.entrySet()) {
			errorOutput.println(entry.getValue().getValue() + " 个 " + getErrorMsg(entry.getKey(), null));
		}
	}
}