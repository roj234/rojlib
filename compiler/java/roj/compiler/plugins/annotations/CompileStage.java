package roj.compiler.plugins.annotations;

import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.BootstrapMethods;
import roj.asm.attr.ConstantValue;
import roj.asm.cp.Constant;
import roj.asm.cp.CstInt;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.collect.HashSet;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.api.Compiler;
import roj.compiler.api.Processor;
import roj.compiler.diagnostic.Kind;
import roj.config.node.IntValue;

import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Roj234
 * @since 2024/6/10 4:27
 */
final class CompileStage implements Processor {
	private static final Set<String> ACCEPTS = new HashSet<>(
		"roj/compiler/plugins/annotations/AutoIncrement",
		"roj/compiler/plugins/annotations/Getter",
		"roj/compiler/plugins/annotations/Setter",
		"roj/compiler/plugins/annotations/Singleton",
		"roj/compiler/plugins/annotations/LenientGeneric"
	);
	@Override public Set<String> acceptedAnnotations() {return ACCEPTS;}

	private final WeakHashMap<Annotation, IntValue> increment_count = new WeakHashMap<>();

	@Override
	public void handle(CompileContext ctx, ClassDefinition file, Attributed node, Annotation annotation) {
		String type = annotation.type();
		type = type.substring(type.lastIndexOf('/')+1);
		CompileUnit cu = (CompileUnit) file;
		switch (type) {
			case "AutoIncrement" -> {
				IntValue start = increment_count.computeIfAbsent(annotation, x -> new IntValue(annotation.getInt("start")));

				if (node.getAttribute("ConstantValue") != null) {
					throw new IllegalStateException("字段"+node+"已经有值");
				}
				((FieldNode) node).addAttribute(new ConstantValue(new CstInt(start.value)));
				cu.finalFields.remove(node);

				start.value += annotation.getInt("step", 1);
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
				c.insn(Opcodes.ALOAD_0);
				c.field(Opcodes.GETFIELD, cu.name(), fn.name(), fn.rawDesc());
				c.insn(fn.fieldType().getOpcode(Opcodes.IRETURN));
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
				c.insn(Opcodes.ALOAD_0);
				c.varLoad(fType, 1);
				c.field(Opcodes.PUTFIELD, cu.name(), fn.name(), fn.rawDesc());
				c.insn(Opcodes.RETURN);
				c.finish();
			}
			case "Singleton" -> {
				var getInstance = (MethodNode) node;
				if ((getInstance.modifier&Opcodes.ACC_STATIC) == 0 || !getInstance.rawDesc().startsWith("()") || getInstance.rawDesc().equals("()V")) {
					ctx.report(Kind.ERROR, "plugins.annotations.singleton");
					return;
				}

				cu.setMinimumBinaryCompatibility(Compiler.JAVA_11);

				var getter = annotation.getString("value");
				int mid = cu.getMethod(getter, getInstance.rawDesc());

				CodeWriter newMn;
				if (mid < 0) {
					newMn = cu.newMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, getter, getInstance.rawDesc());
				} else {
					var mn = cu.methods().get(mid);
					newMn = cu.newMethod(mn.modifier, mn.name(), mn.rawDesc());
					cu.methods.remove(mid);
				}

				var realGetInstance = cu.cp.getMethodHandle(BootstrapMethods.Kind.INVOKESTATIC, cu.cp.getRefByType(getInstance.owner(), getInstance.name(), getInstance.rawDesc(), Constant.METHOD));
				var idx = cu.addLambdaRef(new BootstrapMethods.Item(
						cu.cp.getRefByType(
								"java/lang/invoke/ConstantBootstraps",
								"invoke",
								"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;",
								Constant.METHOD
						),
						realGetInstance
				));

				newMn.visitSize(getInstance.returnType().length(), 0);
				newMn.ldc(cu.cp.getLoadDyn(idx, getInstance.name(), getInstance.returnType().toDesc()));
				newMn.insn(Opcodes.ARETURN);
				newMn.finish();
			}
			case "LenientGeneric" -> {
				ctx.lenientGenericCast = true;
			}
		}
	}
}