package roj.debug;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.frame.FrameVisitor;
import roj.asm.insn.Label;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.TransformException;
import roj.asmx.launcher.Autoload;
import roj.asmx.launcher.Tweaker;
import roj.ci.annotation.IndirectReference;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/6/30 19:15
 */
@Autoload(group = "debug")
public final class DumpTransformer implements ConstantPoolHooks.Hook<MethodNode> {
	static {
		Tweaker.CONDITIONAL.annotatedMethod("roj/annotation/DumpOnException", new DumpTransformer());
	}

	@Override
	public boolean transform(ClassNode context, MethodNode node) throws TransformException {
		var c = context.newMethod(node.modifier, node.name(), node.rawDesc());

		int base;
		int size = TypeHelper.paramSize(node.rawDesc());
		if ((node.modifier&ACC_STATIC) == 0) {
			size++;
			base = 1;
			c.insn(ALOAD_0);
		} else {
			base = 0;
		}
		if (!node.rawDesc().endsWith(")V") && size == 0) {
			size = 1;
		}
		c.visitSize(size, size);
		c.computeFrames(FrameVisitor.COMPUTE_FRAMES);

		var pars = node.parameters();
		for (int i = 0; i < pars.size(); i++) {
			Type type = pars.get(i);
			c.varLoad(type, base);
			base += type.length();
		}

		node.name(node.name()+"$capture");
		node.modifier = (char) (node.modifier & ~(ACC_PROTECTED|ACC_PUBLIC) | ACC_PRIVATE);

		Label start = c.label();
		c.invoke((node.modifier&ACC_STATIC) == 0 ? INVOKESPECIAL : INVOKESTATIC, node.owner(), node.name(), node.rawDesc());
		c.insn(node.returnType().getOpcode(IRETURN));
		Label end = c.label();

		c.invokeS("roj/debug/DumpTransformer", "_exceptionCaptured", "(Ljava/lang/Throwable;)Ljava/lang/Throwable;");
		c.insn(ATHROW);

		c.visitExceptions();
		c.visitException(start, end, end, null);

		c.finish();
		return true;
	}

	@IndirectReference
	public static Throwable _exceptionCaptured(Throwable ex) {
		DebugTool.heapdump();
		return ex;
	}
}