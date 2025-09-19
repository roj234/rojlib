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
public interface Parser {
	default ConfigValue parse(File file) throws IOException, ParseException {
		var emitter = new TreeEmitter();
		parse(file, emitter);
		return emitter.get();
	}
	default void parse(File file, ValueEmitter emitter) throws IOException, ParseException {
		try (var in = new FileInputStream(file)) {
			parse(in, emitter);
		}
	}

	default ConfigValue parse(DynByteBuf buf) throws IOException, ParseException {
		var emitter = new TreeEmitter();
		parse(buf, emitter);
		return emitter.get();
	}
	default void parse(DynByteBuf buf, ValueEmitter emitter) throws IOException, ParseException {
		parse(buf.asInputStream(), emitter);
	}

	default ConfigValue parse(InputStream in) throws IOException, ParseException {
		var emitter = new TreeEmitter();
		parse(in, emitter);
		return emitter.get();
	}
	void parse(InputStream in, ValueEmitter emitter) throws IOException, ParseException;

	default int flags() {return 0;}
}