package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.collect.SimpleList;
import roj.compiler.context.CompileContext;
import roj.text.CharList;

/**
 * 主要是处理权限和泛型
 * @author Roj234
 * @since 2024/2/6 2:21
 */
final class FieldList extends ComponentList {
	final SimpleList<IClass> owners = new SimpleList<>();
	final SimpleList<FieldNode> fields = new SimpleList<>();
	private int childId;

	void add(IClass klass, FieldNode mn) {
		owners.add(klass);
		fields.add(mn);
	}

	boolean pack(IClass klass) {
		for (int i = 0; i < fields.size(); i++) {
			if (!owners.get(i).equals(klass)) {
				childId = i;
				return false;
			}
		}
		childId = fields.size();
		return childId == 1;
	}

	@NotNull
	public FieldResult findField(CompileContext ctx, int flags) {
		SimpleList<FieldNode> fields = this.fields;
		int size = (flags&THIS_ONLY) != 0 ? childId : fields.size();

		for (int j = 0; j < size; j++) {
			IClass owner = owners.get(j);
			FieldNode fn = fields.get(j);

			if (ctx.checkAccessible(owner, fn, (flags&IN_STATIC) != 0, false)) {
				return new FieldResult(owner, fn);
			}
		}

		CharList sb = new CharList().append("dotGet.incompatible.plural:");

		sb.append(fields.get(0).name()).append(":");

		CharList tmp = new CharList();
		ctx.errorCapture = (trans, param) -> {
			tmp.clear();
			tmp.append(trans);
			for (Object o : param)
				tmp.append(':').append(o);
		};

		for (int i = 0; i < size; i++) {
			IClass owner = owners.get(i);
			FieldNode fn = fields.get(i);

			ctx.checkAccessible(owner, fn, (flags&IN_STATIC) != 0, true);
			sb.append("  {symbol.field}").append(owner.name()).append('.').append(fn.name());
			sb.append("{invoke.not_applicable}{").append(tmp).append("}\n");
			tmp.clear();
		}

		ctx.errorCapture = null;
		tmp._free();
		return new FieldResult(sb.replace('/', '.').toStringAndFree());
	}
}