package roj.config;

import roj.config.data.CEntry;
import roj.config.serial.CVisitor;
import roj.config.serial.ToEntry;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.*;

/**
 * @author Roj234
 * @since 2023/3/17 0017 23:30
 */
public interface BinaryParser {
	default CEntry parseRaw(File file) throws IOException, ParseException {
		return parseRaw(file, 0);
	}
	default CEntry parseRaw(File file, int flag) throws IOException, ParseException {
		return parseRaw(file, new ToEntry(), flag).get();
	}
	default <T extends CVisitor> T parseRaw(File file, T cc, int flag) throws IOException, ParseException {
		try (FileInputStream in = new FileInputStream(file)) {
			return parseRaw(in, cc, flag);
		}
	}

	default CEntry parseRaw(DynByteBuf buf) throws IOException, ParseException {
		return parseRaw(buf, 0);
	}
	default CEntry parseRaw(DynByteBuf buf, int flag) throws IOException, ParseException {
		return parseRaw(buf, new ToEntry(), flag).get();
	}
	default <T extends CVisitor> T parseRaw(DynByteBuf buf, T cc, int flag) throws IOException, ParseException {
		return parseRaw(buf.asInputStream(), cc, flag);
	}

	default CEntry parseRaw(InputStream in) throws IOException, ParseException {
		return parseRaw(in, 0);
	}
	default CEntry parseRaw(InputStream in, int flag) throws IOException, ParseException {
		return parseRaw(in, new ToEntry(), flag).get();
	}
	<T extends CVisitor> T parseRaw(InputStream in, T cc, int flag) throws IOException, ParseException;

	void serialize(CEntry entry, DynByteBuf out) throws IOException;
	default void serialize(CEntry entry, OutputStream out) throws IOException {
		try (ByteList.WriteOut out1 = new ByteList.WriteOut(out)) {
			serialize(entry, out1);
			out1.setOut(null);
		}
	}

	default int availableFlags() { return 0; }
	String format();
}
