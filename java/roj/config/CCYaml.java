package roj.config;

import roj.config.serial.CVisitor;
import roj.config.word.Word;

import static roj.config.CCJson.adaptError;
import static roj.config.JSONParser.*;
import static roj.config.word.Word.EOF;

/**
 * @author Roj234
 * @since 2023/3/19 0019 10:30
 */
public final class CCYaml extends YAMLParser implements CCParser {
	public CCYaml() {}
	public CCYaml(int flag) { super(flag); }

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

	private void ccLineArray() throws ParseException {
		int superIndent = prevIndent;
		int firstIndent = indent;
		if (firstIndent < superIndent) throw err("下级缩进("+firstIndent+")<上级("+superIndent+")");

		cc.valueList();
		int size = 0;

		while (true) {
			int line = LN;
			Word w = next();
			if (LN > line && indent <= firstIndent) {
				cc.valueNull();
				size++;
			} else {
				retractWord();
				//addComment();
				try {
					prevIndent = firstIndent;
					ccElement(flag);
				} catch (ParseException e) {
					throw e.addPath("["+size+"]");
				} finally {
					prevIndent = superIndent;
				}
				size++;
				w = next();
			}

			int off;
			if (w.type() != delim || superIndent < 0 || (off = indent) < firstIndent) {
				retractWord();
				break;
			} else if (off != firstIndent) throw err("缩进有误:"+off+"/"+firstIndent);
		}
		clearComment();
		cc.pop();
	}
	private void ccObject(Word w) throws ParseException {
		int superIndent = prevIndent;
		int firstIndent = indent;
		if (firstIndent <= superIndent) throw err("下级缩进("+firstIndent+")<=上级("+superIndent+")");

		cc.valueMap();
		cyl:
		while (true) {
			String name = w.val();
			switch (w.type()) {
				case ask: throw err("配置文件不用字符串做key是坏文明");
				case join: throw err("CConsumer不支持回退式更改");
				case Word.LITERAL:
				case Word.STRING:
				case Word.INTEGER:
				case Word.LONG:
				case Word.DOUBLE:
				case Word.FLOAT:
				case NULL:
					except(colon, ":");

					cc.key(name);
					//addComment();
					try {
						prevIndent = firstIndent;
						ccElement(flag);
					} catch (ParseException e) {
						throw e.addPath('.'+name);
					} finally {
						prevIndent = superIndent;
					}
					break;
				case Word.EOF: break cyl;
				default: unexpected(w.val(), "字符串");
			}

			w = nextNN();
			if (w.type() == EOF) break;

			int indent = this.indent;
			if (indent < firstIndent) {
				// 上一个是List
				if (firstIndent == Integer.MAX_VALUE) {
					firstIndent = indent;
					continue;
				}

				retractWord();
				break;
			} else if (indent != firstIndent) throw err("缩进有误:"+indent+"/"+firstIndent);
		}

		clearComment();
		cc.pop();
	}
	public void ccElement(int flag) throws ParseException {
		Word w = next();
		String cnt = w.val();
		try {
			switch (w.type()) {
				case join: case force_cast: case ref: case anchor: throw err("访问者模式不支持seek-past");
				case left_m_bracket: CCJson.jsonList(this, flag|LITERAL_KEY); break;
				case left_l_bracket: CCJson.jsonMap(this, flag|LITERAL_KEY); break;
				case multiline: case multiline_clump: cc.value(cnt); break;
				case Word.STRING, Word.LITERAL: if (!checkMap()) cc.value(cnt); break;
				case Word.DOUBLE, Word.FLOAT: {
					double d = w.asDouble();
					if (!checkMap()) cc.value(d);
					break;
				}
				case Word.INTEGER: {
					int i = w.asInt();
					if (!checkMap()) cc.value(i);
					break;
				}
				case Word.RFCDATE_DATE: cc.valueDate(w.asLong()); break;
				case Word.RFCDATE_DATETIME, Word.RFCDATE_DATETIME_TZ: cc.valueTimestamp(w.asLong()); break;
				case Word.LONG: cc.value(w.asLong()); break;
				case TRUE: case FALSE: {
					boolean b = w.type() == TRUE;
					if (!checkMap()) cc.value(b);
					break;
				}
				case NULL: if (!checkMap()) cc.valueNull(); break;
				case delim:
					if (prevLN == LN && LN != 1) {
						if ((flag&LENIENT) == 0) throw err("一行内不允许放置多级列表 (你看的不累吗) (通过LENIENT参数关闭该限制)");
						prevIndent = -1;
					}
					ccLineArray();
					break;
				case Word.EOF: cc.valueNull(); break;
				default: unexpected(cnt); break;
			}
		} catch (Exception e) {
			if (e instanceof ParseException) throw e;
			else throw adaptError(this,e);
		}
	}

	public CVisitor cc() { return cc; }

	private boolean checkMap() throws ParseException {
		mark();

		int i = prevIndex;
		Word firstKey = tmpKey.init(wd.type(), wd.pos(), wd.val());

		int ln = prevLN;
		if (nextNN().type() == colon) {
			if (indent <= prevIndent) {
				if (prevLN == ln) {
					if ((flag&LENIENT) == 0) throw err("一行内不允许同时放置列表和映射 (通过LENIENT参数关闭该限制)");
					//' - 'key: val
					//单引号部分
					//第一个字符: indent
					//第二个字符: +1
					//第三个字符: wd.pos-wd.val.length - i
					indent = indent+1-i+wd.pos()-wd.val().length();
				} else {
					retract();
					retractWord();
					cc.valueNull();
				}
			}

			retract();
			ccObject(firstKey);
			return true;
		}

		retract();
		return false;
	}
}