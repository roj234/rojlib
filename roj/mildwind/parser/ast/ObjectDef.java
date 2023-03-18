package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsMap;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * 操作符 - 定义映射
 *
 * @author Roj233
 * @since 2020/10/15 22:47
 */
final class ObjectDef implements Expression {
	static final ObjectDef EMPTY = new ObjectDef(Collections.emptyMap());

	private final Map<Object, Expression> expr;
	JsMap inst;

	public ObjectDef(Map<Object, Expression> args) {
		this.expr = args;
		this.inst = new JsMap();

		for (Iterator<Map.Entry<Object, Expression>> it = args.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Object, Expression> entry = it.next();

			Expression expr = (entry.getValue() == null ? (Expression)entry.getKey() : entry.getValue()).compress();
			if (expr.isConstant()) {
				if (expr == entry.getValue()) {
					inst.put(entry.getKey().toString(), expr.constVal());
				} else {
					JsObject map = expr.constVal();
					Iterator<JsObject> kit = map._keyItr();
					while (kit.hasNext()) {
						String name = kit.next().toString();
						inst.put(name, map.get(name));
					}
				}
				it.remove();
			} else {
				entry.setValue(expr);
			}
		}
	}

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		int fid = tree.sync(inst);
		tree.load(fid);

		for (Map.Entry<Object, Expression> entry : expr.entrySet()) {
			Expression expr = entry.getValue();
			if (entry.getKey().getClass() == String.class) {
				tree.one(Opcodes.DUP);
				tree.ldc(entry.getKey().toString());
				expr.write(tree, false);
				tree.invokeV("roj/mildwind/type/JsObject", "put", "(Ljava/lang/String;Lroj/mildwind/type/JsObject;)V");
			} else {
				tree.one(Opcodes.DUP);
				expr.write(tree, false);
				tree.invokeV("roj/mildwind/type/JsObject", "putAll", "(Lroj/mildwind/type/JsObject;)V");
			}
		}
	}

	@Nonnull
	@Override
	public Expression compress() { return expr.isEmpty() ? Constant.valueOf(inst) : this; }

	@Override
	public JsObject compute(JsObject ctx) {
		final JsMap v = (JsMap) inst.shallowCOWInstance();
		if (!expr.isEmpty()) {
			for (Map.Entry<Object, Expression> entry : expr.entrySet()) {
				Object key = entry.getKey();
				if (key.getClass() == String.class) {
					v.put(key.toString(), entry.getValue().compute(ctx));
				} else {
					v.putAll(entry.getValue().compute(ctx));
				}
			}
		}
		return v;
	}

	@Override
	public Type type() { return Type.OBJECT; }

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof ObjectDef)) return false;
		ObjectDef define = (ObjectDef) left;

		// todo FIX equals(inst)
		return expr.equals(define.expr) && false;
	}

	@Override
	public String toString() {
		return "ObjectDef{" + "expr=" + expr + ", object=" + inst + '}';
	}
}
