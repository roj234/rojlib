package roj.excel;

import roj.collect.TrieTree;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.StreamWriter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/6/2 0002 14:12
 */
public class CsvWriter implements TableWriter {
	private static final TrieTree<MutableInt> CSV_ESCAPE = new TrieTree<>();
	static {
		CSV_ESCAPE.put(",", null);
		CSV_ESCAPE.put("\n", null);
		CSV_ESCAPE.put("\"", null);
	}

	private final StreamWriter sw;
	public CsvWriter(File file) throws IOException {this.sw = StreamWriter.to(file);}
	public CsvWriter(File file, Charset charset) throws IOException {this.sw = StreamWriter.to(file,charset);}
	public CsvWriter(StreamWriter sw) {this.sw = sw;}

	@Override
	public void writeRow(List<?> row) throws IOException {
		CharList sb = new CharList();
		for (int i = 0; i < row.size();) {
			sb.clear();
			sb.append(row.get(i) == null ? "" : row.get(i).toString());
			i++;

			if (!sb.containsAny(CSV_ESCAPE, true)) sw.append(sb);
			else sw.append('"').append(sb.replace("\"", "\"\"")).append('"');

			sw.append(i==row.size()?'\n':',');
		}
	}

	@Override
	public void close() throws IOException { sw.close(); }
	@Override
	public void flush() throws IOException { sw.flush(); }
}
