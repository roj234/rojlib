package roj.excel;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/5/5 0005 15:15
 */
public interface TableWriter extends Closeable, Flushable {
	default void beginSheet(String name) {
		throw new UnsupportedOperationException();
	}

	/**
	 * String|Object => str
	 * Number => num
	 * Null|"" => null
	 */
	void writeRow(List<?> row) throws IOException;
}
