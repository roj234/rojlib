package roj.config;

import roj.config.serial.CVisitor;

import java.util.Collections;
import java.util.Map;

import static roj.config.Word.*;

/**
 * @author Roj234
 * @since 2023/3/19 10:24
 */
final class CCJson extends JSONParser implements CCParser {
	public CCJson(int flag) {super(flag);}
	public final Map<String, Integer> dynamicFlags() {return Collections.emptyMap();}

	private CVisitor cc;
	@Override
	public <CV extends CVisitor> CV parse(CharSequence text, int flag, CV cv) throws ParseException {
		cc = cv;
		init(text);
		try {
			ccElement(flag);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			cc = null;
			input = null;
		}
		return cv;
	}

	static <T extends Parser&CCParser> void jsonList(T wr, int flag) throws ParseException {
		boolean more = true;
		int size = 0;

		wr.cc().valueList();
		o:
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case rBracket: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				default: wr.retractWord();
			}

			if (!more) wr.unexpected(w.val(), "逗号");
			more = false;

			if (wr.comment != null && wr.comment.length() != 0) {
				wr.cc().comment(wr.comment.toString());
				wr.comment.clear();
			}

			try {
				wr.ccElement(flag);
			} catch (ParseException e) {
				throw e.addPath("["+size+"]");
			}
			size++;
		}

		if (wr.comment != null) wr.comment.clear();
		wr.cc().pop();
	}
	@SuppressWarnings("fallthrough")
	static <T extends Parser&CCParser> void jsonMap(T wr, int flag) throws ParseException {
		boolean more = true;

		wr.cc().valueMap();
		o:
		while (true) {
			Word name = wr.next();
			switch (name.type()) {
				case rBrace: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				case STRING, LITERAL: break;
				default: wr.unexpected(name.val(), more ? "字符串" : "逗号");
			}

			if (!more) wr.unexpected(name.val(), "逗号");
			more = false;

			String k = name.val();

			wr.except(colon, ":");

			if (wr.comment != null && wr.comment.length() != 0) {
				wr.cc().comment(wr.comment.toString());
				wr.comment.clear();
			}

			wr.cc().key(k);
			try {
				wr.ccElement(flag);
			} catch (ParseException e) {
				throw e.addPath('.'+k);
			}
		}
		if (wr.comment != null) wr.comment.clear();
		wr.cc().pop();
	}
	static ParseException adaptError(Parser wr, Exception e) {
		ParseException err = wr.err(e.getClass().getName()+": "+e.getMessage());
		err.setStackTrace(e.getStackTrace());
		return err;
	}

	@SuppressWarnings("fallthrough")
	public void ccElement(int flag) throws ParseException {
		Word w = next();
		try {
			switch (w.type()) {
				default -> unexpected(w.val());
				case lBracket -> jsonList(this, flag);
				case lBrace -> jsonMap(this, flag);
				case LITERAL, STRING -> cc.value(w.val());
				case NULL -> cc.valueNull();
				case TRUE -> cc.value(true);
				case FALSE -> cc.value(false);
				case INTEGER -> cc.value(w.asInt());
				case LONG -> cc.value(w.asLong());
				case DOUBLE -> cc.value(w.asDouble());
			}
		} catch (Exception e) {
			if (e instanceof ParseException) throw e;
			else throw adaptError(this,e);
		}
	}
	public CVisitor cc() { return cc; }
}