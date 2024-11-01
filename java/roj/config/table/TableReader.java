package roj.config.table;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/11/8 0008 19:43
 */
public interface TableReader {
	default boolean readSheet(int sheetId, @Nullable String sheetName) {return true;}
	default void onSheet(int sheetId, String sheetName, String sheetDimension) {}

	void onRow(int rowNumber, List<String> value);
	default void onMergedRow(int colStart, int rowStart, int colEnd, int rowEnd) {}
}
