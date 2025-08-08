package roj.config;

import org.intellij.lang.annotations.MagicConstant;
import roj.config.serial.CVisitor;

import java.util.Collections;
import java.util.Map;

import static roj.config.Token.*;

/**
 * @author Roj234
 * @since 2023/3/19 10:24
 */
final class StreamJson extends JSONParser implements StreamParser {
	public StreamJson(int flag) {super(flag);}

	private CVisitor v;
	@Override
	public <CV extends CVisitor> CV parse(CharSequence text, @MagicConstant(flags = {}) int flag, CV cv) throws ParseException {
		v = cv;
		init(text);
		try {
			streamElement(flag);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			v = null;
			input = null;
		}
		return cv;
	}

	static <T extends Parser& StreamParser> void jsonList(T wr, int flag) throws ParseException {
		boolean more = true;
		int size = 0;

		wr.visitor().valueList();
		o:
		while (true) {
			Token w = wr.next();
			switch (w.type()) {
				case rBracket: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				default: wr.retractWord();
			}

			if (!more) wr.unexpected(w.text(), "逗号");
			more = false;

			if (wr.comment != null && wr.comment.length() != 0) {
				wr.visitor().comment(wr.comment.toString());
				wr.comment.clear();
			}

			try {
				wr.streamElement(flag);
			} catch (ParseException e) {
				throw e.addPath("["+size+"]");
			}
			size++;
		}

		if (wr.comment != null) wr.comment.clear();
		wr.visitor().pop();
	}
	@SuppressWarnings("fallthrough")
	static <T extends Parser&StreamParser> void jsonMap(T wr, int flag) throws ParseException {
		boolean more = true;

		wr.visitor().valueMap();
		o:
		while (true) {
			Token name = wr.next();
			switch (name.type()) {
				case rBrace: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				case STRING, LITERAL: break;
				default: wr.unexpected(name.text(), more ? "字符串" : "逗号");
			}

			if (!more) wr.unexpected(name.text(), "逗号");
			more = false;

			String k = name.text();

			wr.except(colon, ":");

			if (wr.comment != null && wr.comment.length() != 0) {
				wr.visitor().comment(wr.comment.toString());
				wr.comment.clear();
			}

			wr.visitor().key(k);
			try {
				wr.streamElement(flag);
			} catch (ParseException e) {
				throw e.addPath('.'+k);
			}
		}
		if (wr.comment != null) wr.comment.clear();
		wr.visitor().pop();
	}
	static ParseException adaptError(Parser wr, Exception e) {
		ParseException err = wr.err(e.getClass().getName()+": "+e.getMessage());
		err.setStackTrace(e.getStackTrace());
		return err;
	}

	@SuppressWarnings("fallthrough")
	public void streamElement(@MagicConstant(flags = {}) int flag) throws ParseException {
		Token w = next();
		try {
			switch (w.type()) {
				default -> unexpected(w.text());
				case lBracket -> jsonList(this, flag);
				case lBrace -> jsonMap(this, flag);
				case LITERAL, STRING -> v.value(w.text());
				case NULL -> v.valueNull();
				case TRUE -> v.value(true);
				case FALSE -> v.value(false);
				case INTEGER -> v.value(w.asInt());
				case LONG -> v.value(w.asLong());
				case DOUBLE -> v.value(w.asDouble());
			}
		} catch (Exception e) {
			if (e instanceof ParseException) throw e;
			else throw adaptError(this,e);
		}
	}
	public CVisitor visitor() { return v; }
}