package roj.config.table;

import roj.archive.zip.ZipFileWriter;
import roj.config.CsvParser;
import roj.config.ParseException;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.text.TextWriter;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @since 2024/11/8 0008 19:47
 */
public interface TableParser {
	static TableParser xlsxParser() {return new XlsxParser();}
	static TableParser csvParser() {return new CsvParser();}

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

	default void table(File file, TableReader listener) throws IOException, ParseException {table(new FileSource(file), null, listener);}
	default void table(File file, Charset charset, TableReader listener) throws IOException, ParseException {table(new FileSource(file), charset, listener);}
	void table(Source file, Charset charset, TableReader listener) throws IOException, ParseException;
}
