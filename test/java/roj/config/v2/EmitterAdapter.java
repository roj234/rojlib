package roj.config.v2;

import roj.config.ValueEmitter;
import roj.text.ParseException;

import java.io.IOException;

import static roj.config.v2.StreamParser2.*;

/**
 * @author Roj234
 * @since 2025/4/29 5:40
 */
public class EmitterAdapter {
	public static void accept(StreamParser2 parser, ValueEmitter visitor) throws ParseException, IOException {
		new EmitterAdapter(parser, visitor).parseValue();
	}

	private final StreamParser2 parser;
	private final ValueEmitter emitter;

	EmitterAdapter(StreamParser2 parser, ValueEmitter emitter) {
		this.parser = parser;
		this.emitter = emitter;
	}

	void parseValue() throws ParseException, IOException {
		switch (parser.nextToken()) {
			case TOKEN_MAP -> parseObject();
			case TOKEN_ARRAY -> parseArray();
			case TOKEN_STRING -> emitter.emit(parser.getString());
			case TOKEN_NULL -> emitter.emitNull();
			case TOKEN_BOOL -> emitter.emit(parser.getBoolean());
			case TOKEN_INT -> emitter.emit(parser.getInt());
			case TOKEN_INT64 -> emitter.emit(parser.getLong());
			case TOKEN_FLOAT -> emitter.emit(parser.getFloat());
			case TOKEN_FLOAT64 -> emitter.emit(parser.getDouble());
			default -> throw parser.error("不支持的token类型："+parser.peekToken());
		}
	}

	private void parseObject() throws ParseException, IOException {
		emitter.emitMap();
		try {
			while (!parser.nextIsMapEnd()) {
				String key = parser.getString();
				parser.nextMapKey();
				emitter.emitKey(key);
				try {
					parseValue();
				} catch (ParseException e) {
					throw e.addPath('.'+key);
				}
			}
		} finally {
			emitter.pop();
		}
	}

	private void parseArray() throws ParseException, IOException {
		emitter.emitList();
		int index = 0;
		while (!parser.nextIsArrayEnd()) {
			try {
				parseValue();
			} catch (ParseException e) {
				throw e.addPath("["+(index)+"]");
			}
			index++;
		}
		emitter.pop();
	}
}
