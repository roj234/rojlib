package roj.compiler.diagnostic;

import roj.text.CharList;
import roj.ui.AnsiString;
import roj.ui.Terminal;

import java.io.PrintStream;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * @author solo6975
 * @since 2020/12/31 22:22
 */
public class TextDiagnosticReporter implements Consumer<Diagnostic> {
	public boolean errorOnly;

	int err, warn;
	int[] counter = new int[6];

	public TextDiagnosticReporter(int maxError, int maxWarn, int warnOps) {
		this.err = maxError;
		this.warn = maxWarn;
	}

	private String getErrorType(Kind kind) {
		counter[kind.ordinal()]++;
		return switch (kind) {
			case INCOMPATIBLE -> "INCOMPATIBLE";
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
		if (diag.getKind().ordinal() < Kind.ERROR.ordinal() && errorOnly) return;

		CharList sb = new CharList();

		if (diag.getFile() != null) {
			String file = diag.getFile();
			sb.append(file).append(':');
			if (diag.getLineNumber() > 0) sb.append(diag.getLineNumber()).append(':');
			sb.append(' ');
		}

		sb.append(getErrorType(diag.getKind())).append(':').append(' ').append(diag.getMessage(Locale.CHINA));

		if (diag.getLine() != null) {
			sb.append('\n');
			String line = diag.getLine();

			if (diag.getLength() > 0) {
				sb.append(line, 0, diag.getColumnNumber());

				// 多行
				if (diag.getColumnNumber()+diag.getLength() <= line.length()) {
					AnsiString as = new AnsiString(line.substring(diag.getColumnNumber(), diag.getColumnNumber() + diag.getLength()));
					as.bgColorRGB(0xff3333).append(new AnsiString("").clear()).writeAnsi(sb);

					sb.append(line, diag.getColumnNumber()+diag.getLength(), line.length());
				} else {
					AnsiString as = new AnsiString(line.substring(diag.getColumnNumber()));
					as.bgColorRGB(0xff3333).append(new AnsiString("").clear()).writeAnsi(sb);
				}
			} else {
				sb.append(line).append('\n');

				int realWidth = -1;
				for (int i = 0; i < diag.getColumnNumber(); i++) {
					realWidth += Terminal.getCharWidth(line.charAt(i));
				}
				sb.padEnd(' ', realWidth).append('^');
			}
		}

		System.err.println(sb);
		StackTraceElement[] trace = new Throwable().getStackTrace();
		for (int i = 2; i < trace.length; i++) {
			String name = trace[i].getMethodName();
			if (!name.equals("report") && !name.equals("fireDiagnostic") && !name.equals("castTo") && !name.equals("writeCast")) {
				System.err.println("    at "+trace[i]);
				break;
			}
		}
	}

	public void printSum() {
		PrintStream err = System.err;
		err.println((counter[2]+counter[3]) + " 个 警告");
		err.println((counter[4]+counter[5]) + " 个 错误");
	}
}