package roj.config.table;

import roj.archive.zip.ZipFileWriter;
import roj.io.source.Source;
import roj.text.TextWriter;

import java.io.Closeable;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @since 2023/5/5 0005 15:15
 */
public interface TableWriter extends Closeable, Flushable {
	static TableWriter csvWriter(String pathname) throws IOException {return csvWriter(new File(pathname));}
	static TableWriter csvWriter(File file) throws IOException {return new CsvWriter(TextWriter.to(file));}
	static TableWriter csvWriterAppend(String pathname) throws IOException {return csvWriterAppend(new File(pathname));}
	static TableWriter csvWriterAppend(File file) throws IOException {return new CsvWriter(TextWriter.append(file));}
	static TableWriter csvWriter(Closeable out) {return new CsvWriter(new TextWriter(out, null));}

	static TableWriter csvWriter(String pathname, String charset) throws IOException {return csvWriter(new File(pathname), Charset.forName(charset));}
	static TableWriter csvWriter(File file, Charset charset) throws IOException {return new CsvWriter(TextWriter.to(file, charset));}
	static TableWriter csvWriterAppend(String pathname, String charset) throws IOException {return csvWriterAppend(new File(pathname), Charset.forName(charset));}
	static TableWriter csvWriterAppend(File file, Charset charset) throws IOException {return new CsvWriter(TextWriter.append(file, charset));}
	static TableWriter csvWriter(Closeable out, Charset charset) {return new CsvWriter(new TextWriter(out, charset));}

	static XlsxWriter xlsxWriter(File file) throws IOException {return new XlsxWriter(new ZipFileWriter(file));}
	static XlsxWriter xlsxWriter(Source source) throws IOException {return new XlsxWriter(new ZipFileWriter(source, Deflater.DEFAULT_COMPRESSION));}
	static XlsxWriter xlsxWriter(File file, int compression) throws IOException {return new XlsxWriter(new ZipFileWriter(file, compression, 0));}
	static XlsxWriter xlsxWriter(Source source, int compression) throws IOException {return new XlsxWriter(new ZipFileWriter(source, compression));}

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