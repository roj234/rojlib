package roj.compiler.plugins.annotations;

import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.CstInt;
import roj.asm.tree.Attributed;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.BootstrapMethods;
import roj.asm.tree.attr.ConstantValue;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashSet;
import roj.compiler.CompilerSpec;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.api.Processor;
import roj.config.data.CInt;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Roj234
 * @since 2024/6/10 0010 4:27
 */
public class AnnotationProcessor1 implements Processor {
	private static final Set<String> ACCEPTS = new MyHashSet<>(
		"roj/compiler/plugins/annotations/AutoIncrement",
		"roj/compiler/plugins/annotations/Getter",
		"roj/compiler/plugins/annotations/Setter",
		"roj/compiler/plugins/annotations/Singleton"
	);
	@Override
	public Set<String> acceptedAnnotations() {return ACCEPTS;}

	private final WeakHashMap<Annotation, CInt> increment_count = new WeakHashMap<>();

	@Override
	public void handle(LocalContext ctx, IClass file, Attributed node, Annotation annotation) {
		String type = annotation.type();
		type = type.substring(type.lastIndexOf('/')+1);
		CompileUnit cu = (CompileUnit) file;
		switch (type) {
			case "AutoIncrement" -> {
				CInt start = increment_count.computeIfAbsent(annotation, x -> new CInt(annotation.getInt("start")));

				cu.cancelTask(node);
				((FieldNode) node).putAttr(new ConstantValue(new CstInt(start.value)));

				start.value += annotation.getInt("step");
			}
			case "Getter" -> {
				var fn = (FieldNode) node;

				String fnName = "get"+fn.name();
				String fnDesc = "()"+fn.rawDesc();
				if (cu.getMethod(fnName, fnDesc) >= 0) {
					ctx.report(Kind.SEVERE_WARNING, "Getter已存在！");
					return;
				}

				var c = cu.newMethod(Opcodes.ACC_PUBLIC, fnName, fnDesc);
				c.visitSizeMax(fn.fieldType().length(), 1);
				c.one(Opcodes.ALOAD_0);
				c.field(Opcodes.GETFIELD, cu.name, fn.name(), fn.rawDesc());
				c.one(fn.fieldType().shiftedOpcode(Opcodes.IRETURN));
				c.finish();
			}
			case "Setter" -> {
				var fn = (FieldNode) node;
				Type fType = fn.fieldType();

				String fnName = "set"+fn.name();
				String fnDesc = "("+fn.rawDesc()+")V";
				if (cu.getMethod(fnName, fnDesc) >= 0) {
					ctx.report(Kind.SEVERE_WARNING, "Setter已存在！");
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
			case "Singleton" -> {
				var gi = (MethodNode) node;
				if ((gi.modifier&Opcodes.ACC_STATIC) == 0 || !gi.rawDesc().startsWith("()") || gi.rawDesc().equals("()V")) {
					ctx.report(Kind.ERROR, "plugins.annotations.singleton");
					return;
				}

				cu.setMinimumBinaryCompatibility(CompilerSpec.COMPATIBILITY_LEVEL_JAVA_11);

				var getter = annotation.getString("value");
				int mid = cu.getMethod(getter, gi.rawDesc());

				CodeWriter newMn;
				if (mid < 0) {
					newMn = cu.newMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, getter, gi.rawDesc());
				} else {
					var mn = cu.methods().get(mid);
					newMn = cu.newMethod(mn.modifier, mn.name(), mn.rawDesc());
					cu.methods.remove(mid);
				}

				var realGetInstance = cu.cp.getMethodHandle(gi.owner, gi.name(), gi.rawDesc(), BootstrapMethods.Kind.INVOKESTATIC, Constant.METHOD);
				var idx = cu.addLambdaRef(new BootstrapMethods.Item("java/lang/invoke/ConstantBootstraps", "invoke",
					"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;",
					BootstrapMethods.Kind.INVOKESTATIC, Constant.METHOD, Collections.singletonList(realGetInstance)));

				newMn.visitSize(gi.returnType().length(), 0);
				newMn.ldc(cu.cp.getInvokeDyn(false, idx, gi.name(), gi.returnType().toDesc()));
				newMn.one(Opcodes.ARETURN);
				newMn.finish();
			}
		}
	}
}