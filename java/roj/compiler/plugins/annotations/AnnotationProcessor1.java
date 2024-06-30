package roj.compiler.plugins.annotations;

import roj.asm.Opcodes;
import roj.asm.cp.CstInt;
import roj.asm.tree.Attributed;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.ConstantValue;
import roj.asm.type.Type;
import roj.collect.MyHashSet;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.api.Processor;
import roj.config.data.CInt;

import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Roj234
 * @since 2024/6/10 0010 4:27
 */
public class AnnotationProcessor1 implements Processor {
	private static final Set<String> ACCEPTS = new MyHashSet<>("roj/compiler/plugins/annotations/AutoIncrement", "roj/compiler/plugins/annotations/Getter", "roj/compiler/plugins/annotations/Setter");
	@Override
	public Set<String> acceptedAnnotations() {return ACCEPTS;}

	private final WeakHashMap<Annotation, CInt> increment_count = new WeakHashMap<>();

	@Override
	public void handle(LocalContext ctx, IClass file, Attributed node, Annotation annotation) {
		String type = annotation.type();
		CompileUnit cu = (CompileUnit) file;
		if (type.endsWith("AutoIncrement")) {
			CInt start = increment_count.computeIfAbsent(annotation, x -> new CInt(annotation.getInt("start")));

			cu.cancelTask(node);
			((FieldNode) node).putAttr(new ConstantValue(new CstInt(start.value)));

			start.value += annotation.getInt("step");
		} else if (type.endsWith("Getter")) {
			FieldNode fn = (FieldNode) node;

			String fnName = "get"+fn.name();
			String fnDesc = "()"+fn.rawDesc();
			if (cu.getMethod(fnName, fnDesc) >= 0) {
				ctx.report(Kind.WARNING, "Getter已存在！");
				return;
			}

			var c = cu.newMethod(Opcodes.ACC_PUBLIC, fnName, fnDesc);
			c.visitSizeMax(fn.fieldType().length(), 1);
			c.one(Opcodes.ALOAD_0);
			c.field(Opcodes.GETFIELD, cu.name, fn.name(), fn.rawDesc());
			c.one(fn.fieldType().shiftedOpcode(Opcodes.IRETURN));
			c.finish();
		} else if (type.endsWith("Setter")) {
			FieldNode fn = (FieldNode) node;
			Type fType = fn.fieldType();

			String fnName = "set"+fn.name();
			String fnDesc = "("+fn.rawDesc()+")V";
			if (cu.getMethod(fnName, fnDesc) >= 0) {
				ctx.report(Kind.WARNING, "Setter已存在！");
				return;
			}

			var c = cu.newMethod(Opcodes.ACC_PUBLIC, fnName, fnDesc);
			c.visitSizeMax(fType.length()+1, fType.length()+1);
			c.one(Opcodes.ALOAD_0);
			c.varLoad(fType, 1);
			c.field(Opcodes.PUTFIELD, cu.name, fn.name(), fn.rawDesc());
			c.one(Opcodes.RETURN);
			c.finish();
		}
	}
}