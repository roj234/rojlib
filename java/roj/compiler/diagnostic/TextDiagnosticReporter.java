package roj.compiler.diagnostic;

import roj.text.CharList;
import roj.ui.Terminal;
import roj.ui.Text;

import java.io.PrintStream;
import java.util.Locale;
import java.util.function.Function;

/**
 * @author solo6975
 * @since 2020/12/31 22:22
 */
public class TextDiagnosticReporter implements Function<Diagnostic, Boolean> {
	public int warnOps;
	public int total, err, warn;
	final int[] counter = new int[6];

	public TextDiagnosticReporter(int maxError, int maxWarn, int warnOps) {
		this.err = maxError;
		this.warn = maxWarn;
		this.warnOps = warnOps;
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
	public Boolean apply(Diagnostic diag) {
		total++;

		if (diag.getKind().ordinal() < Kind.ERROR.ordinal() && (warnOps&1) != 0) return false;

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
					Text as = new Text(line.substring(diag.getColumnNumber(), diag.getColumnNumber() + diag.getLength()));
					as.bgColorRGB(0xff3333).append(new Text("").reset()).writeAnsi(sb);

					sb.append(line, diag.getColumnNumber()+diag.getLength(), line.length());
				} else {
					Text as = new Text(line.substring(diag.getColumnNumber()));
					as.bgColorRGB(0xff3333).append(new Text("").reset()).writeAnsi(sb);
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
			if (!name.equals("report") && !name.equals("fireDiagnostic") && !name.equals("castTo") && !name.equals("writeCast") && !name.equals("endCodeBlock")) {
				System.err.println("    at "+trace[i]);
				break;
			}
		}

		return diag.getKind().ordinal() >= (((warnOps&2) != 0) ? Kind.WARNING.ordinal() : Kind.ERROR.ordinal());
	}

	public void summary() {
		PrintStream err = System.err;
		err.println((counter[2]+counter[3]) + " 个 警告");
		err.println((counter[4]+counter[5]) + " 个 错误");
	}
}