package roj.table;

import roj.collect.TrieTree;
import roj.config.data.CInt;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.TextWriter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/6/2 0002 14:12
 */
public class CsvWriter implements TableWriter {
	private static final TrieTree<CInt> CSV_ESCAPE = new TrieTree<>();
	static {
		CSV_ESCAPE.put(",", null);
		CSV_ESCAPE.put(";", null);
		CSV_ESCAPE.put("\n", null);
		CSV_ESCAPE.put("\"", null);
	}

	private final TextWriter sw;
	public CsvWriter(File file) throws IOException {this.sw = TextWriter.to(file);}
	public CsvWriter(File file, Charset charset) throws IOException {this.sw = TextWriter.to(file,charset);}
	public CsvWriter(TextWriter sw) {this.sw = sw;}

	@Override
	public void writeRow(List<?> row) throws IOException {
		CharList sb = new CharList();
		for (int i = 0; i < row.size();) {
			sb.clear();
			Object obj = row.get(i);
			sb.append(obj == null ? "" : obj.toString());
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