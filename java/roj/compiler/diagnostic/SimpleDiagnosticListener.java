package roj.compiler.diagnostic;

import roj.text.CharList;
import roj.ui.CLIUtil;

import java.io.PrintStream;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * @author solo6975
 * @since 2020/12/31 22:22
 */
public class SimpleDiagnosticListener implements Consumer<Diagnostic> {
	int err, warn;
	int[] counter = new int[5];

	public SimpleDiagnosticListener(int maxError, int maxWarn, int warnOps) {
		this.err = maxError;
		this.warn = maxWarn;
	}

	private String getErrorType(Kind kind) {
		counter[kind.ordinal()]++;
		return switch (kind) {
			case NOTE -> "注";
			case WARNING -> "警告";
			case SEVERE_WARNING -> "强警告";
			case ERROR, INTERNAL_ERROR -> "错误";
		};
	}

	/**
	 * Invoked when a problem is found.
	 *
	 * @param diag a diagnostic representing the problem that
	 * was found
	 *
	 * @throws NullPointerException if the diagnostic argument is
	 * {@code null} and the implementation cannot handle {@code null}
	 * arguments
	 */
	@Override
	public void accept(Diagnostic diag) {
		CharList sb = new CharList();

		if (diag.getFilePath() != null) {
			String file = diag.getFilePath();
			sb.append(file).append(':');
			if (diag.getLineNumber() > 0) sb.append(diag.getLineNumber()).append(':');
			sb.append(' ');
		}

		sb.append(getErrorType(diag.getKind())).append(':').append(' ').append(diag.getMessage(Locale.CHINA)).append('\n');

		if (diag.getLine() != null) {
			sb.append(diag.getLine()).append('\n');
			int realWidth = -1;
			for (int i = 0; i < diag.getColumnNumber(); i++) {
				realWidth += CLIUtil.getCharWidth(diag.getLine().charAt(i));
			}
			sb.padEnd(' ', realWidth).append('^').append('\n');
		}

		System.err.print(sb);
		StackTraceElement[] trace = new Throwable().getStackTrace();
		for (int i = 2; i < trace.length; i++) {
			String name = trace[i].getMethodName();
			if (!name.equals("report") && !name.equals("fireDiagnostic")) {
				System.err.println("\tat "+trace[i]);
				break;
			}
		}
	}

	public void conclusion() {
		PrintStream err = System.err;
		err.println((counter[1]+counter[2]) + " 个 警告");
		err.println((counter[3]+counter[4]) + " 个 错误");
	}
}