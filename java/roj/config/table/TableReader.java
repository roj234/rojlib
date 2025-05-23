package roj.config.table;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/11/8 19:43
 */
public interface TableReader {
	default boolean readSheet(int sheetId, @Nullable String sheetName) {return true;}
	default void onSheet(int sheetId, String sheetName, String sheetDimension) {}

	/**
	 * @param rowNumber 行号，从1开始，不是0！
	 * @param value
	 */
	void onRow(@Range(from = 1, to = Integer.MAX_VALUE) int rowNumber, List<String> value);
	default void onMergedRow(int colStart, int rowStart, int colEnd, int rowEnd) {}
}
