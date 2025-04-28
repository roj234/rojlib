package roj.config.v2;

import roj.config.ParseException;
import roj.config.serial.CVisitor;

import java.io.IOException;

import static roj.config.v2.StreamParser.*;

/**
 * @author Roj234
 * @since 2025/4/29 0029 5:40
 */
public class StreamVisitorAdapter {
	public static void accept(StreamParser parser, CVisitor visitor) throws ParseException, IOException {
		new StreamVisitorAdapter(parser, visitor).parseValue();
	}

	private final StreamParser parser;
	private final CVisitor visitor;

	StreamVisitorAdapter(StreamParser parser, CVisitor visitor) {
		this.parser = parser;
		this.visitor = visitor;
	}

	void parseValue() throws ParseException, IOException {
		switch (parser.nextToken()) {
			case TOKEN_MAP -> parseObject();
			case TOKEN_ARRAY -> parseArray();
			case TOKEN_STRING -> visitor.value(parser.getString());
			case TOKEN_NULL -> visitor.valueNull();
			case TOKEN_BOOL -> visitor.value(parser.getBoolean());
			case TOKEN_INT -> visitor.value(parser.getInt());
			case TOKEN_INT64 -> visitor.value(parser.getLong());
			case TOKEN_FLOAT -> visitor.value(parser.getFloat());
			case TOKEN_FLOAT64 -> visitor.value(parser.getDouble());
			default -> throw parser.error("不支持的token类型："+parser.peekToken());
		}
	}

	private void parseObject() throws ParseException, IOException {
		visitor.valueMap();
		try {
			while (!parser.nextIsMapEnd()) {
				String key = parser.getString();
				parser.nextMapKey();
				visitor.key(key);
				try {
					parseValue();
				} catch (ParseException e) {
					throw e.addPath('.'+key);
				}
			}
		} finally {
			visitor.pop();
		}
	}

	private void parseArray() throws ParseException, IOException {
		visitor.valueList();
		int index = 0;
		while (!parser.nextIsArrayEnd()) {
			try {
				parseValue();
			} catch (ParseException e) {
				throw e.addPath("["+(index)+"]");
			}
			index++;
		}
		visitor.pop();
	}
}
