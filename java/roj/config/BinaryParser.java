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
	default CEntry parseRaw(File file) throws IOException, ParseException { return parseRaw(file, 0); }
	default CEntry parseRaw(File file, int flag) throws IOException, ParseException { return parseRaw(new ToEntry(), file, flag).get(); }
	default <T extends CVisitor> T parseRaw(T cc, File file, int flag) throws IOException, ParseException {
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			return parseRaw(cc, in, flag);
		}
	}

	default CEntry parseRaw(DynByteBuf buf) throws IOException, ParseException { return parseRaw(buf, 0); }
	default CEntry parseRaw(DynByteBuf buf, int flag) throws IOException, ParseException { return parseRaw(new ToEntry(), buf, flag).get(); }
	default <T extends CVisitor> T parseRaw(T cc, DynByteBuf buf, int flag) throws IOException, ParseException { return parseRaw(cc, buf.asInputStream(), flag); }

	default CEntry parseRaw(InputStream in) throws IOException, ParseException { return parseRaw(in, 0); }
	default CEntry parseRaw(InputStream in, int flag) throws IOException, ParseException { return parseRaw(new ToEntry(), in, flag).get(); }
	<T extends CVisitor> T parseRaw(T cc, InputStream in, int flag) throws IOException, ParseException;

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
