package roj.config.table;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/5/5 0005 15:15
 */
public interface TableWriter extends Closeable, Flushable {
	default void writeRow() throws IOException {writeRow(Collections.emptyList());}
	default void writeRow(Object row) throws IOException {writeRow(Collections.singletonList(row));}
	default void writeRow(Object... row) throws IOException {writeRow(Arrays.asList(row));}
	/**
	 * String|Object => str
	 * Number => num
	 * Null => null
	 */
	void writeRow(List<?> row) throws IOException;
}