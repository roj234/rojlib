package roj.config;

import roj.config.data.CEntry;
import roj.config.serial.CVisitor;
import roj.config.serial.ToEntry;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/3/17 23:30
 */
public interface BinaryParser {
	default CEntry parse(File file) throws IOException, ParseException { return parse(file, 0); }
	default CEntry parse(File file, int flag) throws IOException, ParseException { return parse(file, flag, new ToEntry()).get(); }
	default <T extends CVisitor> T parse(File file, int flag, T cc) throws IOException, ParseException {
		try (FileInputStream in = new FileInputStream(file)) {
			return parse(in, flag, cc);
		}
	}

	default CEntry parse(DynByteBuf buf) throws IOException, ParseException { return parse(buf, 0); }
	default CEntry parse(DynByteBuf buf, int flag) throws IOException, ParseException { return parse(buf, flag, new ToEntry()).get(); }
	default <T extends CVisitor> T parse(DynByteBuf buf, int flag, T cc) throws IOException, ParseException { return parse(buf.asInputStream(), flag, cc); }

	default CEntry parse(InputStream in) throws IOException, ParseException { return parse(in, 0); }
	default CEntry parse(InputStream in, int flag) throws IOException, ParseException { return parse(in, flag, new ToEntry()).get(); }
	<T extends CVisitor> T parse(InputStream in, int flag, T cc) throws IOException, ParseException;

	default int flags() {return 0;};
}