package roj.ci.plugin;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Attribute;
import roj.asm.frame.FrameVisitor;
import roj.asm.insn.Code;
import roj.asm.insn.InsnList;
import roj.asm.insn.InsnNode;
import roj.asm.type.Type;
import roj.ci.BuildContext;
import roj.config.node.ConfigValue;
import roj.config.node.MapValue;

import java.util.List;
import java.util.Objects;

import static roj.compiler.runtime.RtUtil.CLASS_NAME;

/**
 * @author Roj234
 * @since 2026/1/20 19:05
 */
public class JBAnnotations implements Plugin {
	@Override public String name() {return "约束注解处理程序 (JetBrains)";}
	@Override public boolean defaultEnabled() {return true;}

	private boolean returnEnable, returnDefaultEnforce, parameterEnable, parameterDefaultEnforce;

	@Override
	public void init(ConfigValue config) {
		MapValue map = config.asMap();
		returnEnable = map.getBool("returnEnable", true);
		returnDefaultEnforce = map.getBool("returnDefaultEnforce", false);
		parameterEnable = map.getBool("parameterEnable", true);
		parameterDefaultEnforce = map.getBool("parameterDefaultEnforce", true);
	}

	@Override
	public void process(BuildContext ctx) {
		// 注意，事件驱动的转换必须在post阶段之前调用
		// 另外还可以搞MagicConstants，但Retention=SOURCE
		if (returnEnable) {
			ctx.classNodeEvents().annotatedMethod("org/jetbrains/annotations/NotNull", (ClassNode context, MethodNode method) -> {
				Code code = method.getCode(context.cp);
				if (code == null) return false;

				var attribute = method.getAttribute(context.cp, Attribute.InvisibleAnnotations);
				Annotation notNull = Objects.requireNonNull(Annotation.find(attribute.annotations, "org/jetbrains/annotations/NotNull"));
				if (!notNull.getBool("enforce", returnDefaultEnforce)) return false;

				var returnType = method.returnType().getActualType();
				if (returnType != Type.OBJECT) return false;

				InsnList instructions = code.instructions;
				for (InsnNode node : instructions) {
					if (node.opcode() == Opcodes.ARETURN) {
						var list = new InsnList();

						list.insn(Opcodes.DUP);
						list.ldc("return value");
						list.invokeS(CLASS_NAME, "nullCheck", "(Ljava/lang/Object;Ljava/lang/String;)V");

						node.insertBefore(list);
					}
				}

				code.computeFrames(FrameVisitor.COMPUTE_SIZES);
				return true;
			});
			ctx.classNodeEvents().annotatedMethod("org/jetbrains/annotations/Range", (ClassNode context, MethodNode method) -> {
				Code code = method.getCode(context.cp);
				if (code == null) return false;

				var attribute = method.getAttribute(context.cp, Attribute.InvisibleAnnotations);
				Annotation range = Annotation.poll(attribute.annotations, "org/jetbrains/annotations/Range");
				if (range == null || !range.getBool("enforce", returnDefaultEnforce)) return false;

				var returnType = method.returnType().getActualType();
				if (returnType != Type.LONG && returnType != Type.INT) return false;

				InsnList instructions = code.instructions;
				for (InsnNode node : instructions) {
					if (node.opcode() == Opcodes.IRETURN) {
						var list = new InsnList();

						list.ldc("return value");
						list.ldc(range.getInt("from", Integer.MIN_VALUE));
						list.ldc(range.getInt("to", Integer.MAX_VALUE));
						list.invokeS(CLASS_NAME, "rangeCheck", "(ILjava/lang/String;II)I");

						node.insertBefore(list);
					}
					if (node.opcode() == Opcodes.LRETURN) {
						var list = new InsnList();

						list.ldc("return value");
						list.ldc(range.getLong("from", Long.MIN_VALUE));
						list.ldc(range.getLong("to", Long.MAX_VALUE));
						list.invokeS(CLASS_NAME, "rangeCheck", "(JLjava/lang/String;JJ)J");

						node.insertBefore(list);
					}
				}

				code.computeFrames(FrameVisitor.COMPUTE_SIZES);
				return true;
			});
		}
		if (parameterEnable) {
			ctx.classNodeEvents().annotatedParameter("org/jetbrains/annotations/NotNull", (ClassNode context, MethodNode method) -> {
				Code code = method.getCode(context.cp);
				if (code == null) return false;

				var attribute = method.getAttribute(context.cp, Attribute.InvisibleParameterAnnotations);
				var mp = method.getAttribute(context.cp, Attribute.MethodParameters);

				InsnList preInsert = new InsnList();

				int slot = (method.modifier & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
				List<Type> parameters = method.parameters();
				List<List<Annotation>> annotations = attribute.annotations;
				for (int i = 0; i < annotations.size(); i++) {
					Annotation notNull = Annotation.find(annotations.get(i), "org/jetbrains/annotations/NotNull");
					Type type = parameters.get(i);
					if (notNull != null && notNull.getBool("enforce", parameterDefaultEnforce) && type.getActualType() == Type.OBJECT) {
						preInsert.vars(Opcodes.ALOAD, slot); // assert this is Object type ?
						preInsert.ldc(mp == null || mp.getName(i, null) == null ? "par"+i : mp.getName(i, null));
						preInsert.invokeS(CLASS_NAME, "nullCheck", "(Ljava/lang/Object;Ljava/lang/String;)V");
					}

					slot += type.length();
				}

				if (preInsert.length() == 0) return false;

				code.instructions.getNodeAt(0).insertBefore(preInsert);
				code.computeFrames(FrameVisitor.COMPUTE_SIZES); // 这应该会检查ALOAD的正确性
				return true;
			});
			ctx.classNodeEvents().annotatedParameter("org/jetbrains/annotations/Range", (ClassNode context, MethodNode method) -> {
				Code code = method.getCode(context.cp);
				if (code == null) return false;

				var attribute = method.getAttribute(context.cp, Attribute.InvisibleParameterAnnotations);
				var mp = method.getAttribute(context.cp, Attribute.MethodParameters);

				InsnList preInsert = new InsnList();

				int slot = (method.modifier & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
				List<Type> parameters = method.parameters();
				List<List<Annotation>> annotations = attribute.annotations;
				for (int i = 0; i < annotations.size(); i++) {
					Annotation range = Annotation.poll(annotations.get(i), "org/jetbrains/annotations/Range");
					Type type = parameters.get(i);
					if (range != null && range.getBool("enforce", parameterDefaultEnforce) && (type.getActualType() == Type.LONG || type.getActualType() == Type.INT)) {

						preInsert.varLoad(type, slot);
						preInsert.ldc(mp == null || mp.getName(i, null) == null ? "par"+i : mp.getName(i, null));

						if (type.type == Type.LONG) {
							preInsert.ldc(range.getLong("from", Long.MIN_VALUE));
							preInsert.ldc(range.getLong("to", Long.MAX_VALUE));
							preInsert.invokeS(CLASS_NAME, "rangeCheck", "(JLjava/lang/String;JJ)J");
							preInsert.insn(Opcodes.POP2);
						} else {
							preInsert.ldc(range.getInt("from", Integer.MIN_VALUE));
							preInsert.ldc(range.getInt("to", Integer.MAX_VALUE));
							preInsert.invokeS(CLASS_NAME, "rangeCheck", "(ILjava/lang/String;II)I");
							preInsert.insn(Opcodes.POP);
						}
					}

					slot += parameters.get(i).length();
				}

				if (preInsert.length() == 0) return false;

				code.instructions.getNodeAt(0).insertBefore(preInsert);
				code.computeFrames(FrameVisitor.COMPUTE_SIZES);

				return true;
			});
		}
	}
}