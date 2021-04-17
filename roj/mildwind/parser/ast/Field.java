package roj.mildwind.parser.ast;

import roj.collect.MyHashSet;
import roj.config.word.NotStatementException;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsBool;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

import javax.annotation.Nonnull;

import static roj.mildwind.parser.ast.ExprParser.OP_DEL;
import static roj.mildwind.parser.ast.ExprParser.OP_OPTIONAL;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Field implements LoadExpression {
	Expression parent;
	String name;
	byte flag;

	static final MyHashSet<String> fast_access = new MyHashSet<>("length", "prototype", "__proto__");

	public Field(Expression parent, String name, int flag) {
		this.parent = parent;
		this.name = name;
		this.flag = (byte) flag;
	}

	@Nonnull
	@Override
	public Expression compress() {
		parent = parent.compress();
		return parent.isConstant() ? Constant.valueOf(parent.constVal().get(name)) : this;
	}

	@Override
	public Type type() {
		if ((flag&OP_DEL) != 0) return Type.BOOL;
		return parent.isConstant() ? parent.constVal().get(name).type() : Type.OBJECT;
	}

	public boolean setDeletion() { flag |= OP_DEL; return true; }

	public void writeLoad(JsMethodWriter tree) { parent.write(tree, false); }
	public void writeExecute(JsMethodWriter tree, boolean noRet) {
		if ((flag&OP_DEL) == 0 && fast_access.contains(name)) {
			if (noRet) throw new NotStatementException();
			tree.invokeV("roj/mildwind/type/JsObject", name, "()Lroj/mildwind/type/JsObject;");
			return;
		}

		tree.ldc(name);
		if ((flag&OP_DEL) != 0) {
			tree.invokeV("roj/mildwind/type/JsObject", "del", "(Ljava/lang/String;)Z");
			tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
		} else {
			if (noRet) throw new NotStatementException();
			tree.invokeV("roj/mildwind/type/JsObject", "get", "(Ljava/lang/String;)V");
		}
	}

	public JsObject compute(JsObject ctx) {
		JsObject v = parent.compute(ctx);
		return (flag&OP_DEL) != 0 ? JsBool.valueOf(v.del(name)?1:0) : v.get(name);
	}
	public void computeAssign(JsObject ctx, JsObject val) {
		parent.compute(ctx).put(name, val);
	}

	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Field)) return false;
		Field field = (Field) left;
		return field.parent.isEqual(parent) && field.name.equals(name) && field.flag == flag;
	}
	public String toString() { return parent+((flag&OP_OPTIONAL)!=0?"?.":".")+name; }
}
