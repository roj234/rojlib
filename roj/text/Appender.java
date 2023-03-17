package roj.text;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/3/18 0018 0:03
 */
public interface Appender extends Appendable {
	Appender append(char c) throws IOException;
	default Appender append(CharSequence csq) throws IOException {
		if (csq == null) return append("null");
		return append(csq, 0, csq.length());
	}
	Appender append(CharSequence csq, int start, int end) throws IOException;

	Appender append(int num) throws IOException;
	Appender append(long num) throws IOException;
	Appender append(float num) throws IOException;
	Appender append(double num) throws IOException;
	default Appender append(Object o) throws IOException {
		return append(o == null ? "null" : o.toString());
	}
}
