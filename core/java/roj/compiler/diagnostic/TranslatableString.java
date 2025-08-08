package roj.compiler.diagnostic;

import roj.asm.ClassDefinition;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.type.IType;
import roj.compiler.ast.expr.Expr;
import roj.config.*;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ArrayCache;

/**
 * @author Roj234
 * @since 2025/4/4 3:22
 */
public class TranslatableString {
	public final String i18nKey;
	public final Object[] arguments;

	public TranslatableString(String i18nKey, Object... arguments) {
		this.i18nKey = i18nKey;
		this.arguments = arguments;
	}

	public static TranslatableString of(String i18nKey) {return new TranslatableString(i18nKey, ArrayCache.OBJECTS);}
	public static TranslatableString of(String i18nKey, Object... arguments) {return new TranslatableString(i18nKey, arguments == null ? ArrayCache.OBJECTS : arguments);}

	@Override
	public String toString() {return translate(I18n.NULL, IOUtil.getSharedCharBuf()).toString();}

	public CharList translate(I18n i18n, CharList buf) {
		String translate = i18n.translate(i18nKey);
		if (arguments.length == 0 && translate.equals(i18nKey) && (translate.endsWith("]") || translate.endsWith("\""))) {
			var wr = new JSONParser().init(translate);
			try {
				translateList(wr, i18n, buf);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		} else {
			buf.append(translate);
			for (int i = 0; i < arguments.length; i++) {
				Object arg = arguments[i];
				CharSequence str;
				if (arg instanceof TranslatableString ts) {
					str = ts.translate(i18n, new CharList()).toStringAndFree();
				} else if (arg instanceof ClassDefinition classNode) {
					str = classNode.name().replace('/', '.');
				} else if (arg instanceof MethodNode methodNode) {
					str = methodNode.toString();
				} else if (arg instanceof FieldNode fieldNode) {
					str = fieldNode.toString();
				} else if (arg instanceof IType type) {
					str = type.toString();
				}  else if (arg instanceof Expr expr) {
					str = expr.toString();
				} else {
					str = of(arg.toString()).translate(i18n, new CharList()).toStringAndFree();
				}

				String placeholder = "%"+(i+1);
				int pos = buf.indexOf(placeholder);
				if (pos < 0) buf.append(str);
				else buf.replace(placeholder, str);
			}
		}

		return buf.replace("<nae.unresolvable>", "<解析失败>");
	}

	private static void translateList(Tokenizer wr, I18n i18n, CharList buf) throws ParseException {
		Token w;
		do {
			w = wr.next();
			if (w.type() == Token.LITERAL) {
				var str = i18n.translate(w.text());

				w = wr.next();
				if (w.type() != 17) {
					wr.retractWord();
					buf.append(str);
				}
				else translateLabel(wr, i18n, buf, str);
			}
			else if (w.type() == 14) translateList(wr, i18n, buf);
			else buf.append(w.text());

			w = wr.next();
		} while (w.type() == 16);
		if (w.type() != 15 && w.type() != Token.EOF) wr.unexpected("exception list end");
	}
	private static void translateLabel(Tokenizer wr, I18n i18n, CharList buf, String tr) throws ParseException {
		buf.append(tr);

		var w = wr.next();
		if (w.type() != 14) {
			if (w.type() == 16) {
				wr.retractWord();
				return;
			}

			buf.replace("%1", w.text());
		} else {
			var oneWord = new CharList();

			int i = 0;
			do {
				w = wr.next();
				if (w.type() == Token.LITERAL) {
					var str = i18n.translate(w.text());

					w = wr.next();
					if (w.type() != 17) {
						wr.retractWord();
						oneWord.append(str);
					}
					else translateLabel(wr, i18n, oneWord, str);
				}
				else if (w.type() == 14) translateList(wr, i18n, oneWord);
				else oneWord.append(w.text());

				String placeholder = "%"+ ++ i;
				int pos = buf.indexOf(placeholder);
				if (pos < 0) buf.append(oneWord);
				else buf.replace(placeholder, oneWord);

				oneWord.clear();

				w = wr.next();
			} while (w.type() == 16);

			oneWord._free();
			if (w.type() != 15 && w.type() != Token.EOF) wr.unexpected("exception list end");
		}
	}
}
