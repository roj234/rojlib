package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassDefinition;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.api.Compiler;
import roj.compiler.api.FieldAccessHook;
import roj.text.CharList;

/**
 * 主要是处理权限和泛型
 * @author Roj234
 * @since 2024/2/6 2:21
 */
final class FieldList extends ComponentList {
	final ArrayList<ClassDefinition> owners = new ArrayList<>();
	final ArrayList<FieldNode> fields = new ArrayList<>();
	private int childId;

	void add(ClassDefinition klass, FieldNode mn) {
		owners.add(klass);
		fields.add(mn);
	}

	boolean pack(ClassDefinition klass) {
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
		ArrayList<FieldNode> fields = this.fields;
		int size = (flags&THIS_ONLY) != 0 ? childId : fields.size();

		for (int j = 0; j < size; j++) {
			ClassDefinition owner = owners.get(j);
			FieldNode fn = fields.get(j);

			if (ctx.checkAccessible(owner, fn, (flags&IN_STATIC) != 0, false)) {
				checkBridgeMethod(ctx, owner, fn);
				checkDeprecation(ctx, owner, fn);
				return new FieldResult(owner, fn);
			}
		}

		CharList sb = new CharList().append("dotGet.incompatible.plural:[");

		sb.append(fields.get(0).name()).append(",[");

		CharList tmp = new CharList();
		ctx.errorCapture = makeErrorCapture(tmp);

		for (int i = 0; i < size; i++) {
			ClassDefinition owner = owners.get(i);
			FieldNode fn = fields.get(i);

			ctx.checkAccessible(owner, fn, (flags&IN_STATIC) != 0, true);
			sb.append("\"  \",symbol.field,").append(owner.name()).append('.').append(fn.name());
			sb.append(",invoke.notApplicable,").append(tmp).append(",\"\n\",");
			tmp.clear();
		}
		sb.setLength(sb.length()-1);

		ctx.errorCapture = null;
		tmp._free();
		return new FieldResult(sb.append("]]").replace('/', '.').toStringAndFree());
	}

	static void checkBridgeMethod(CompileContext ctx, ClassDefinition owner, FieldNode fn) {
		if ((fn.modifier&Opcodes.ACC_PRIVATE) == 0 || ctx.file == owner ||
			ctx.compiler.getMaximumBinaryCompatibility() >= Compiler.JAVA_11) return;

		if (fn.getAttribute(FieldAccessHook.NAME) != null) return;

		var fwr = new FieldBridge((CompileUnit) owner);
		fn.addAttribute(fwr);
	}
}