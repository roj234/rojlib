package roj.asmx.capture;

import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.Label;
import roj.asmx.NodeTransformer;
import roj.asmx.TransformException;
import roj.asmx.launcher.Autoload;
import roj.asmx.launcher.DefaultTweaker;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/6/30 0030 19:15
 */
@Autoload(Autoload.Target.INIT)
public class ExceptionalTransformer implements NodeTransformer<MethodNode> {
	static {
		DefaultTweaker.CONDITIONAL.annotatedMethod("roj/asmx/capture/Exceptional", new ExceptionalTransformer());
	}

	@Override
	public boolean transform(ConstantData cls, MethodNode it) throws TransformException {
		var c = cls.newMethod(it.modifier, it.name(), it.rawDesc());

		int base;
		int size = TypeHelper.paramSize(it.rawDesc());
		if ((it.modifier&ACC_STATIC) == 0) {
			size++;
			base = 1;
			c.one(ALOAD_0);
		} else {
			base = 0;
		}
		if (!it.rawDesc().endsWith(")V") && size == 0) {
			size = 1;
		}
		c.visitSize(size, size);

		var pars = it.parameters();
		for (int i = 0; i < pars.size(); i++) {
			Type type = pars.get(i);
			c.varLoad(type, base);
			base += type.length();
		}

		it.name(it.name()+"$exceptional");
		it.modifier = (char) (it.modifier & ~(ACC_PROTECTED|ACC_PUBLIC) | ACC_PRIVATE);

		Label start = c.label();
		c.invoke((it.modifier&ACC_STATIC) == 0 ? INVOKESPECIAL : INVOKESTATIC, it.owner, it.name(), it.rawDesc());
		c.one(it.returnType().shiftedOpcode(IRETURN));
		Label end = c.label();

		c.visitExceptions();
		c.visitException(start, end, end, null);

		c.invokeS("roj/asmx/capture/ExceptionalTransformer", "ExceptionFired", "(Ljava/lang/Throwable;)Ljava/lang/Throwable;");
		c.one(ATHROW);

		c.finish();
		return true;
	}

	public static Throwable ExceptionFired(Throwable ex) {
		System.out.println("异常已捕获");
		ExceptionServer.start();
		return ex;
	}
}