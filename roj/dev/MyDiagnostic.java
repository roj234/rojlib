package roj.dev;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Locale;

/**
 * @author solo6975
 * @since 2021/10/2 14:15
 */
public class MyDiagnostic implements Diagnostic<JavaFileObject> {
	private final String msg;
	private final Kind kind;

	MyDiagnostic(String msg, Kind kind) {
		this.msg = msg;
		this.kind = kind;
	}

	@Override
	public Kind getKind() {
		return kind;
	}

	@Override
	public JavaFileObject getSource() {
		return null;
	}

	@Override
	public long getPosition() {
		return -1;
	}

	@Override
	public long getStartPosition() {
		return 0;
	}

	@Override
	public long getEndPosition() {
		return 0;
	}

	@Override
	public long getLineNumber() {
		return -1;
	}

	@Override
	public long getColumnNumber() {
		return 0;
	}

	@Override
	public String getCode() {
		return "";
	}

	@Override
	public String getMessage(Locale locale) {
		return msg;
	}
}
