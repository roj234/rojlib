package roj.config.table;

import roj.collect.BitSet;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.TextWriter;

import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/6/2 14:12
 */
final class CsvWriter implements TableWriter {
	private static final BitSet CSV_ESCAPE = BitSet.from(",;\n\"");

	private final TextWriter sw;
	public CsvWriter(TextWriter sw) {this.sw = sw;}

	@Override
	public void writeRow(List<?> row) {
		CharList sb = new CharList();
		for (int i = 0; i < row.size();) {
			sb.clear();
			Object obj = row.get(i);
			if (obj != null) sb.append(obj.toString());
			i++;

			if (sb.containsAny(CSV_ESCAPE) || (!(obj instanceof Number) && TextUtil.isNumber(sb) != -1))
				sw.append('"').append(sb.replace("\"", "\"\"")).append('"');
			else sw.append(sb);

			sw.append(i==row.size()?'\n':',');
		}
	}

	@Override public void close() throws IOException {sw.close();}
	@Override public void flush() throws IOException {sw.flush();}
}