package roj.config;

import roj.config.serial.CVisitor;
import roj.config.word.Word;

import static roj.config.word.Word.*;

/**
 * @author Roj234
 * @since 2023/3/19 0019 10:24
 */
public final class CCJson extends JSONParser implements CCParser {
	public CCJson() {}
	public CCJson(int flag) { super(flag); }

	private CVisitor cc;
	@Override
	public <CV extends CVisitor> CV parse(CV cv, CharSequence text, int flag) throws ParseException {
		this.flag = flag;
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

	static <T extends Parser<?>&CCParser> void jsonList(T wr, int flag) throws ParseException {
		boolean more = true;
		int size = 0;

		wr.cc().valueList();
		o:
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case right_m_bracket: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				default: wr.retractWord();
			}

			if (!more && (flag & LENIENT_COMMA) == 0) wr.unexpected(w.val(), "逗号");
			more = false;

			try {
				wr.ccElement(flag);
			} catch (ParseException e) {
				throw e.addPath("["+size+"]");
			}
			size++;
		}

		wr.cc().pop();
	}
	@SuppressWarnings("fallthrough")
	static <T extends Parser<?>&CCParser> void jsonMap(T wr, int flag) throws ParseException {
		boolean more = true;

		wr.cc().valueMap();
		o:
		while (true) {
			Word name = wr.next();
			switch (name.type()) {
				case right_l_bracket: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				case STRING: break;
				case LITERAL: if ((flag & LITERAL_KEY) != 0) break;
				default: wr.unexpected(name.val(), more ? "字符串" : "逗号");
			}

			if (!more && (flag & LENIENT_COMMA) == 0) wr.unexpected(name.val(), "逗号");
			more = false;

			String k = name.val();

			wr.except(colon, ":");

			wr.cc().key(k);
			try {
				wr.ccElement(flag);
			} catch (ParseException e) {
				throw e.addPath('.'+k);
			}
		}
		wr.cc().pop();
	}
	static ParseException adaptError(Parser<?> wr, Exception e) {
		ParseException err = wr.err(e.getClass().getName() + ": " + e.getMessage());
		err.setStackTrace(e.getStackTrace());
		return err;
	}

	@SuppressWarnings("fallthrough")
	public void ccElement(int flag) throws ParseException {
		Word w = next();
		try {
			switch (w.type()) {
				case left_m_bracket: jsonList(this, flag); break;
				case STRING: cc.value(w.val()); break;
				case DOUBLE: case FLOAT: cc.value(w.asDouble()); break;
				case INTEGER: cc.value(w.asInt()); break;
				case LONG: cc.value(w.asLong()); break;
				case TRUE: cc.value(true); break;
				case FALSE: cc.value(false); break;
				case NULL: cc.valueNull(); break;
				case left_l_bracket: jsonMap(this, flag); break;
				case LITERAL:
					if ((flag & LITERAL_KEY) != 0) {
						cc.value(w.val());
						break;
					}
				default: unexpected(w.val());
			}
		} catch (Exception e) {
			if (e instanceof ParseException) throw e;
			else throw adaptError(this,e);
		}
	}
	public CVisitor cc() { return cc; }
}

