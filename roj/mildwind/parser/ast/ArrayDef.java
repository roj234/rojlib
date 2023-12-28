package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.config.word.NotStatementException;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsArray;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;
import roj.text.CharList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 操作符 - 定义数组
 *
 * @author Roj233
 * @since 2020/10/15 22:47
 */
final class ArrayDef implements Expression {
	static final ArrayDef EMPTY = new ArrayDef(Collections.emptyList());

	JsArray inst;

	int[] dynamic_id_;
	List<Expression> dynamic_;

	public ArrayDef(List<Expression> args) {
		int dynamicSize = 0;
		for (int j = 0; j < args.size(); j++) {
			Expression arg = args.get(j).compress();
			args.set(j, arg);
			if (!arg.isConstant()) {
				// optimizationDisabled = true
				if (arg instanceof Spread) {
					dynamic_ = args;
					return;
				}
				dynamicSize++;
			}
		}

		this.inst = new JsArray(args.size(), true);

		if (dynamicSize > 0) {
			dynamic_id_ = new int[dynamicSize];
			dynamic_ = Arrays.asList(new Expression[dynamicSize]);
		}

		int i = 0;
		for (int j = 0; j < args.size(); j++) {
			Expression arg = args.get(j).compress();
			if (arg instanceof Spread) {
				inst.pushAll(arg.constVal());
			} else if (arg.isConstant()) {
				inst.push(arg.constVal());
			} else {
				dynamic_id_[i] = inst.length;
				dynamic_.set(i, arg);
				i++;
			}
		}
	}

	@Override
	public Type type() { return Type.ARRAY; }

	static boolean arrayEq(Collection<?> args, Collection<?> args1) {
		return args.size() == args1.size() && args.equals(args1);
	}
	static boolean contentEq(JsObject[] a, JsObject[] b) {
		if (a.length != b.length) return false;
		for (int i = 0; i < a.length; i++) {
			if (!a[i].op_feq(b[i])) return false;
		}
		return true;
	}

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		if (inst != null) {
			int fid = tree.sync(inst);
			tree.load(fid);

			if (dynamic_ == null) return;
			for (int i = 0; i < dynamic_.size(); i++) {
				tree.one(Opcodes.DUP);
				tree.ldc(dynamic_id_[i]);
				dynamic_.get(i).write(tree, false);
				tree.invokeV("roj/mildwind/type/JsArray", "putByInt", "(ILroj/mildwind/type/JsObject;)V");
			}
		} else {
			tree.newObject("roj/mildwind/type/JsArray"); // todo use CACHED array
			for (int i = 0; i < dynamic_.size(); i++) {
				Expression expr = dynamic_.get(i);
				tree.one(Opcodes.DUP);
				expr.write(tree, false);
				tree.invokeV("roj/mildwind/type/JsArray", expr instanceof Spread ? "pushAll" : "push", "(Lroj/mildwind/type/JsObject;)V");
			}
		}
	}

	@Override
	public Expression compress() { return dynamic_ == null ? Constant.valueOf(inst) : this; }

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (left == null || getClass() != left.getClass()) return false;
		ArrayDef oth = (ArrayDef) left;
		// todo FIX equals(static_)
		return arrayEq(dynamic_, oth.dynamic_) && false;
	}

	@Override
	public JsArray compute(JsContext ctx) {
		JsArray v;
		if (inst != null) {
			v = (JsArray) inst.shallowCOWInstance();
			if (dynamic_ != null) {
				for (int i = 0; i < dynamic_.size(); i++) {
					v.putByInt(dynamic_id_[i], dynamic_.get(i).compute(ctx));
				}
			}
		} else {
			v = new JsArray();
			for (int i = 0; i < dynamic_.size(); i++) {
				Expression expr = dynamic_.get(i);
				if (expr instanceof Spread) v.pushAll(expr.compute(ctx));
				else v.push(expr.compute(ctx));
			}
		}
		return v;
	}

	@Override
	public String toString() {
		CharList sb;
		if (inst == null) {
			sb = new CharList().append('[');
			int i = 0;
			for (;;) {
				sb.append(dynamic_.get(i));

				if (++i == dynamic_.size()) break;
				sb.append(", ");
			}
		} else {
			if (dynamic_ == null) return inst.toString();

			sb = new CharList().append('[');
			int j = 0;
			JsObject[] list = inst.list;
			for (int i = 0; i < inst.length; i++) {
				if (list[i] == null) sb.append(dynamic_.get(j++));
				else sb.append(list[i]);

				if (++i == inst.length) break;
				sb.append(", ");
			}
		}

		return sb.append(']').toStringAndFree();
	}
}