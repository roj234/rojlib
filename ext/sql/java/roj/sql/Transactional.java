package roj.sql;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.annotation.AList;
import roj.asm.annotation.Annotation;
import roj.asm.frame.FrameVisitor;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.TransformException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2025/07/20 20:41
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Transactional {
	Propagation propagation() default Propagation.REQUIRED;
	Isolation isolation() default Isolation.DEFAULT;
	/**
	 * 发生某些类型的异常时，不回滚
	 */
	Class<? extends Throwable>[] commitFor() default {};

	enum Propagation {REQUIRED, REQUIRED_NEW}
	enum Isolation {DEFAULT, REPEATABLE_READ, SERIALIZABLE}

	class Transformer implements ConstantPoolHooks.Hook<MethodNode> {
		@Override
		public boolean transform(ClassNode context, MethodNode node) throws TransformException {
			Annotation transactional = Annotation.findInvisible(context.cp, node, "roj/sql/Transactional");

			String name = node.name();

			CodeWriter cw = context.newMethod(node.modifier, name, node.rawDesc());
			cw.invokeS("roj/sql/QueryBuilder", "getInstance", "()Lroj/sql/QueryBuilder;");
			cw.invokeV("roj/sql/QueryBuilder", "transBegin", "()Lroj/sql/QueryBuilder;");
			cw.insn(Opcodes.POP);

			Label tryStart = cw.label();

			int slot;
			if ((node.modifier&Opcodes.ACC_STATIC) != 0) {
				cw.insn(Opcodes.ALOAD_0);
				slot = 1;
			} else {
				slot = 0;
			}
			cw.visitSizeMax(Math.max(2, TypeHelper.paramSize(node.rawDesc()) + slot), TypeHelper.paramSize(node.rawDesc()));

			for (Type parameter : node.parameters()) {
				cw.varLoad(parameter, slot);
				slot += parameter.length();
			}

			String newName = name+"$transactional";
			node.name(newName);
			node.modifier = (char) (node.modifier & ~(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED) | Opcodes.ACC_PRIVATE);
			cw.invokeD(context.name(), newName, node.rawDesc());

			cw.return_(node.returnType());

			Label tryEnd = cw.label();

			AList commitFor = transactional.getList("commitFor");
			Label commit = tryEnd;
			if (commitFor.size() > 0) {
				cw.invokeS("roj/sql/QueryBuilder", "getInstance", "()Lroj/sql/QueryBuilder;");
				cw.insn(Opcodes.ICONST_1);
				cw.invokeV("roj/sql/QueryBuilder", "transEnd", "(Z)Lroj/sql/QueryBuilder;");
				cw.insn(Opcodes.ATHROW);
			}

			Label rollback = cw.label();
			cw.invokeS("roj/sql/QueryBuilder", "getInstance", "()Lroj/sql/QueryBuilder;");
			cw.insn(Opcodes.ICONST_0);
			cw.invokeV("roj/sql/QueryBuilder", "transEnd", "(Z)Lroj/sql/QueryBuilder;");
			cw.insn(Opcodes.ATHROW);

			cw.computeFrames(FrameVisitor.COMPUTE_FRAMES);
			cw.visitExceptions();
			for (int i = 0; i < commitFor.size(); i++) {
				cw.visitException(tryStart, tryEnd, commit, commitFor.getType(i).owner);
			}
			cw.visitException(tryStart, tryEnd, rollback, null);
			cw.finish();

			return true;
		}
	}
}
