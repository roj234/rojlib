package roj.compiler.diagnostic;

import roj.asm.FieldNode;
import roj.asm.IClass;
import roj.asm.MethodNode;
import roj.asm.type.IType;
import roj.compiler.ast.expr.ExprNode;
import roj.config.I18n;
import roj.text.CharList;
import roj.util.ArrayCache;

/**
 * @author Roj234
 * @since 2025/4/4 0004 3:22
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

	public CharList translate(I18n i18n, CharList buf) {
		buf.append(i18n.translate(i18nKey));
		for (int i = 0; i < arguments.length; i++) {
			Object arg = arguments[i];
			CharSequence str;
			if (arg instanceof TranslatableString ts) {
				str = ts.translate(i18n, new CharList()).toStringAndFree();
			} else if (arg instanceof IClass classNode) {
				str = classNode.name().replace('/', '.');
			} else if (arg instanceof MethodNode methodNode) {
				str = methodNode.toString();
			} else if (arg instanceof FieldNode fieldNode) {
				str = fieldNode.toString();
			} else if (arg instanceof IType type) {
				str = type.toString();
			}  else if (arg instanceof ExprNode expr) {
				str = expr.toString();
			} else if (arg instanceof String) {
				str = arg.toString();
			} else {
				throw new UnsupportedOperationException("不知道怎么转换" + arg.getClass() + "到字符串");
			}

			buf.replace("%"+(i+1), str);
		}
		return buf;
	}
}
