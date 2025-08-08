package roj.config;

import org.intellij.lang.annotations.MagicConstant;
import roj.config.serial.CVisitor;

import java.util.Map;

import static roj.config.StreamJson.*;
import static roj.config.Flags.LENIENT;
import static roj.config.Token.EOF;

/**
 * @author Roj234
 * @since 2023/3/19 10:30
 */
final class StreamYaml extends YAMLParser implements StreamParser {
	public StreamYaml(int flag) {super(flag);}

	private CVisitor cc;
	@Override
	public <CV extends CVisitor> CV parse(CharSequence text, @MagicConstant(flags = LENIENT) int flag, CV cv) throws ParseException {
		this.flag = flag;
		cc = cv;
		init(text);
		if (!next().text().equals("---")) retractWord();
		try {
			streamElement(flag);
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
			Token w = next();
			if (LN > line && indent <= firstIndent && whiteSpaceUntilNextLine(i)) {
				cc.valueNull();
				size++;
			} else {
				retractWord();
				//addComment();
				try {
					prevIndent = firstIndent;
					streamElement(flag);
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
	private void ccObject(Token w) throws ParseException {
		int superIndent = prevIndent;
		int firstIndent = indent;
		if (firstIndent <= superIndent) throw err("下级缩进("+firstIndent+")<=上级("+superIndent+")");

		cc.valueMap();
		cyl:
		while (true) {
			String name = w.text();
			switch (w.type()) {
				case ask: throw err("配置文件不用字符串做key是坏文明");
				case join: throw err("CConsumer不支持回退式更改");
				case Token.LITERAL, Token.STRING:
				case Token.INTEGER, Token.LONG, Token.DOUBLE, Token.FLOAT:
				case NULL:
					except(colon, ":");

					cc.key(name);
					//addComment();
					try {
						prevIndent = firstIndent;
						streamElement(flag);
					} catch (ParseException e) {
						throw e.addPath('.'+name);
					} finally {
						prevIndent = superIndent;
					}
					break;
				case Token.EOF: break cyl;
				default: unexpected(w.text(), "字符串");
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
	public void streamElement(@MagicConstant(flags = LENIENT) int flag) throws ParseException {
		Token w = next();
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
				case multiline, multiline_clump -> cc.value(w.text());
				case Token.STRING, Token.LITERAL -> {
					var cnt = w.text();
					if (!checkMap()) cc.value(cnt);
				}
				case Token.DOUBLE, Token.FLOAT -> {
					double d = w.asDouble();
					if (!checkMap()) cc.value(d);
				}
				case Token.INTEGER -> {
					int i = w.asInt();
					if (!checkMap()) cc.value(i);
				}
				case Token.RFCDATE_DATE -> cc.valueDate(w.asLong());
				case Token.RFCDATE_DATETIME, Token.RFCDATE_DATETIME_TZ -> cc.valueTimestamp(w.asLong());
				case Token.LONG -> cc.value(w.asLong());
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
				case Token.EOF -> cc.valueNull();
				default -> unexpected(w.text());
			}
		} catch (Exception e) {
			if (e instanceof ParseException) throw e;
			else throw adaptError(this,e);
		}
	}

	public CVisitor visitor() { return cc; }

	private boolean checkMap() throws ParseException {
		mark();

		Token firstKey = tmpKey.init(wd.type(), wd.pos(), wd.text());

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