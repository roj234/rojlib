package roj.config;

import roj.config.node.ConfigValue;
import roj.text.ParseException;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2023/3/17 23:30
 */
public interface BinaryParser {
	default ConfigValue parse(File file) throws IOException, ParseException { return parse(file, 0); }
	default ConfigValue parse(File file, int flag) throws IOException, ParseException { return parse(file, flag, new TreeEmitter()).get(); }
	default <E extends ValueEmitter> E parse(File file, int flag, E emitter) throws IOException, ParseException {
		try (FileInputStream in = new FileInputStream(file)) {
			return parse(in, flag, emitter);
		}
	}

	default ConfigValue parse(DynByteBuf buf) throws IOException, ParseException { return parse(buf, 0); }
	default ConfigValue parse(DynByteBuf buf, int flag) throws IOException, ParseException { return parse(buf, flag, new TreeEmitter()).get(); }
	default <E extends ValueEmitter> E parse(DynByteBuf buf, int flag, E emitter) throws IOException, ParseException { return parse(buf.asInputStream(), flag, emitter); }

	default ConfigValue parse(InputStream in) throws IOException, ParseException { return parse(in, 0); }
	default ConfigValue parse(InputStream in, int flag) throws IOException, ParseException { return parse(in, flag, new TreeEmitter()).get(); }
	<E extends ValueEmitter> E parse(InputStream in, int flag, E emitter) throws IOException, ParseException;

	default int flags() {return 0;}
}