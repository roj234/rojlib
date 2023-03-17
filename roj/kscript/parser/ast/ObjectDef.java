package roj.kscript.parser.ast;

import roj.kscript.Constants;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.type.KObject;
import roj.kscript.type.KString;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 操作符 - 定义映射
 *
 * @author Roj233
 * @since 2020/10/15 22:47
 */
public final class ObjectDef implements Expression {
	public static final ObjectDef EMPTY = new ObjectDef(Collections.emptyMap());

	private final Map<String, Expression> expr;
	KObject object;

	public ObjectDef(Map<String, Expression> args) {
		this.expr = args;
		this.object = new KObject(Constants.OBJECT);

		for (Iterator<Map.Entry<String, Expression>> it = args.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Expression> entry = it.next();

			Expression expr = entry.getValue().compress();
			if (expr.type() != -1) {
				object.put(entry.getKey(), expr.asCst().val());
				it.remove();
			} else {
				entry.setValue(expr);
			}
		}
	}

	@Override
	public KType compute(Map<String, KType> param) {
		final KObject v = (KObject) object.copy();
		if (!expr.isEmpty()) {
			for (Map.Entry<String, Expression> entry : expr.entrySet()) {
				v.put(entry.getKey(), entry.getValue().compute(param));
			}
		}
		return v;
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		tree.Load(object);
		for (Map.Entry<String, Expression> entry : expr.entrySet()) {
			Expression expr = entry.getValue();
			if (expr != null) {
				expr.write(tree.Std(Opcode.DUP).Load(KString.valueOf(entry.getKey())), false);
				tree.Std(Opcode.PUT_OBJ);
			}
		}
	}

	@Nonnull
	@Override
	public Expression compress() {
		return this;
	}

	@Override
	public byte type() {
		return -1;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof ObjectDef)) return false;
		ObjectDef define = (ObjectDef) left;
		return mapEq(expr, define.expr) && define.object.__int_equals__(object);
	}

	private static boolean mapEq(Map<String, Expression> expr, Map<String, Expression> expr1) {
		if (expr.size() != expr1.size()) return false;

		if (expr.isEmpty()) return true;

		Iterator<Map.Entry<String, Expression>> itra;
		Iterator<Map.Entry<String, Expression>> itrb;
		List<Map.Entry<String, Expression>> tmp = new ArrayList<>(expr.entrySet());
		tmp.sort((o1, o2) -> Integer.compare(o1.getKey().hashCode(), o2.getKey().hashCode()));
		itra = tmp.iterator();
		tmp = new ArrayList<>(expr1.entrySet());
		tmp.sort((o1, o2) -> Integer.compare(o1.getKey().hashCode(), o2.getKey().hashCode()));
		itrb = tmp.iterator();


		while (itra.hasNext()) {
			final Map.Entry<String, Expression> next = itra.next();
			final Map.Entry<String, Expression> next1 = itrb.next();
			if (next == null) {
				if (next1 != null) return false;
			} else if (!next.getKey().equals(next1.getKey()) || !next1.getValue().isEqual(next.getValue())) return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "ObjectDef{" + "expr=" + expr + ", object=" + object + '}';
	}
}
