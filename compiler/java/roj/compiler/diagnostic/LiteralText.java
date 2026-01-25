package roj.compiler.diagnostic;

import roj.asm.ClassDefinition;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.NaE;
import roj.compiler.types.VirtualType;
import roj.text.CharList;
import roj.text.I18n;

/**
 * @author Roj234
 * @since 2026/01/27 19:13
 */
public final class LiteralText extends IText {
	private final Object text;

	public LiteralText(Object text) {this.text = text;}

	@Override
	public void appendTo(CharList buf, I18n i18n) {
		staticAppendTo(buf, i18n, text);
		for (IText text : extra) text.appendTo(buf, i18n);
	}

	public static void staticAppendTo(CharList buf, I18n i18n, Object arg) {
		String str;
		if (arg instanceof ClassDefinition classNode) {
			str = classNode.name().replace('/', '.');
		} else if (arg instanceof MethodNode methodNode) {
			Signature signature = methodNode.getAttribute(null, Attribute.SIGNATURE);
			str = TypeHelper.humanize(signature == null ? Type.getMethodTypes(methodNode.rawDesc()) : signature.values, methodNode.name(), false);
		} else if (arg instanceof FieldNode fieldNode) {
			str = fieldNode.name();
		} else if (arg instanceof VirtualType wc) {
			str = i18n.translate("type.special."+wc.getI18nKey());
		} else if (arg instanceof Expr expr) {
			if (expr.type() == NaE.UNRESOLVABLE) {
				str = i18n.translate("type.special.<unresolvable>");
			} else {
				str = expr.toString();
			}
		} else {
			str = String.valueOf(arg);
		}

		buf.append(str);
	}
}
