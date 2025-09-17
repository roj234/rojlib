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
@FunctionalInterface
public interface BinaryParser {
	default ConfigValue parse(File file) throws IOException, ParseException {return parse(file, 0);}
	default ConfigValue parse(File file, int flags) throws IOException, ParseException {
		var emitter = new TreeEmitter();
		parse(file, flags, emitter);
		return emitter.get();
	}
	default void parse(File file, int flags, ValueEmitter emitter) throws IOException, ParseException {
		try (var in = new FileInputStream(file)) {
			parse(in, flags, emitter);
		}
	}

	default ConfigValue parse(DynByteBuf buf) throws IOException, ParseException {return parse(buf, 0);}
	default ConfigValue parse(DynByteBuf buf, int flags) throws IOException, ParseException {
		var emitter = new TreeEmitter();
		parse(buf, flags, emitter);
		return emitter.get();
	}
	default void parse(DynByteBuf buf, int flags, ValueEmitter emitter) throws IOException, ParseException {parse(buf.asInputStream(), flags, emitter);}

	default ConfigValue parse(InputStream in) throws IOException, ParseException {return parse(in, 0);}
	default ConfigValue parse(InputStream in, int flags) throws IOException, ParseException {
		var emitter = new TreeEmitter();
		parse(in, flags, emitter);
		return emitter.get();
	}
	void parse(InputStream in, int flags, ValueEmitter emitter) throws IOException, ParseException;

	default int flags() {return 0;}
}