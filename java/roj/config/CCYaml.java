package roj.config;

import roj.config.serial.CVisitor;

import java.util.Map;

import static roj.config.CCJson.*;
import static roj.config.Flags.LENIENT;
import static roj.config.Word.EOF;

/**
 * @author Roj234
 * @since 2023/3/19 10:30
 */
final class CCYaml extends YAMLParser implements CCParser {
	public CCYaml(int flag) {super(flag);}
	public final Map<String, Integer> dynamicFlags() {return Map.of("Lenient", LENIENT);}

	private CVisitor cc;
	@Override
	public <CV extends CVisitor> CV parse(CharSequence text, int flag, CV cv) throws ParseException {
		this.flag = flag;
		cc = cv;
		init(text);
		if (!next().val().equals("---")) retractWord();
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
			int line = LN, i = index;
			Word w = next();
			if (LN > line && indent <= firstIndent && whiteSpaceUntilNextLine(i)) {
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
			if (w.type() != delim || superIndent == -2 || (off = indent) < firstIndent) {
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
				case Word.LITERAL, Word.STRING:
				case Word.INTEGER, Word.LONG, Word.DOUBLE, Word.FLOAT:
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
		try {
			switch (w.type()) {
				case join, force_cast, ref, anchor -> throw err("访问者模式不支持seek-past");
				case lBracket -> {
					this.flag |= JSON_MODE;
					jsonList(this, flag);
					this.flag ^= JSON_MODE;
				}
				case lBrace -> {
					this.flag |= JSON_MODE;
					jsonMap(this, flag);
					this.flag ^= JSON_MODE;
				}
				case multiline, multiline_clump -> cc.value(w.val());
				case Word.STRING, Word.LITERAL -> {
					var cnt = w.val();
					if (!checkMap()) cc.value(cnt);
				}
				case Word.DOUBLE, Word.FLOAT -> {
					double d = w.asDouble();
					if (!checkMap()) cc.value(d);
				}
				case Word.INTEGER -> {
					int i = w.asInt();
					if (!checkMap()) cc.value(i);
				}
				case Word.RFCDATE_DATE -> cc.valueDate(w.asLong());
				case Word.RFCDATE_DATETIME, Word.RFCDATE_DATETIME_TZ -> cc.valueTimestamp(w.asLong());
				case Word.LONG -> cc.value(w.asLong());
				case TRUE, FALSE -> {
					boolean b = w.type() == TRUE;
					if (!checkMap()) cc.value(b);
				}
				case NULL -> {
					if (!checkMap()) cc.valueNull();
				}
				case delim -> {
					if (prevLN == LN && LN != 1) {
						if ((flag & LENIENT) == 0) throw err("一行内不允许放置多级列表 (你看的不累吗) (通过LENIENT参数关闭该限制)");
						prevIndent = -1;
					}
					ccLineArray();
				}
				case Word.EOF -> cc.valueNull();
				default -> unexpected(w.val());
			}
		} catch (Exception e) {
			if (e instanceof ParseException) throw e;
			else throw adaptError(this,e);
		}
	}

	public CVisitor cc() { return cc; }

	private boolean checkMap() throws ParseException {
		mark();

		Word firstKey = tmpKey.init(wd.type(), wd.pos(), wd.val());

		int ln = prevLN;
		if (nextNN().type() == colon) {
			if (indent <= prevIndent) {
				if (prevLN == ln) {
					if ((flag&LENIENT) == 0) throw err("一行内不允许同时放置列表和映射 (通过LENIENT参数关闭该限制)");
					int begin = firstKey.pos();
					while (begin > 0) {
						indent++;
						if (input.charAt(--begin) == '-') break;
					}
				} else {
					retract();
					retractWord();
					cc.valueNull();
					return true;
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