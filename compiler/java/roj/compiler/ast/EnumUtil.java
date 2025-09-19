package roj.compiler.ast;

import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.api.Types;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.LocalVariable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/11/17 19:02
 */
public class EnumUtil {
	private static final List<LocalVariable> _NAME_ORDINAL;
	static {
		var v1 = new Variable("@name", Types.STRING_TYPE);
		v1.slot = 1;
		v1.hasValue = true;

		var v2 = new Variable("@ordinal", Type.INT_TYPE);
		v2.slot = 2;
		v2.hasValue = true;

		_NAME_ORDINAL = Arrays.asList(new LocalVariable(v1), new LocalVariable(v2));
	}
	public static List<Expr> prependEnumConstructor(List<Expr> nodes) {
		if (nodes instanceof ArrayList<Expr>) {
			nodes.addAll(0, _NAME_ORDINAL);
			return nodes;
		}

		var ref = new ArrayList<Expr>(nodes.size()+2);
		ref.addAll(_NAME_ORDINAL);
		ref.addAll(nodes);
		return ref;
	}

	public static List<Expr> prepend(List<Expr> nodes, Expr that) {
		if (nodes instanceof ArrayList<Expr>) {
			nodes.add(0, that);
			return nodes;
		}

		var ref = new ArrayList<Expr>(nodes.size()+1);
		ref.add(that);
		ref.addAll(nodes);
		return ref;
	}
}
