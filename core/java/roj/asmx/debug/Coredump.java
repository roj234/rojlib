package roj.asmx.debug;

import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.insn.Code;
import roj.asm.insn.Label;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.TransformException;
import roj.asmx.launcher.Autoload;
import roj.asmx.launcher.Tweaker;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/6/30 19:15
 */
@Autoload(Autoload.Target.INIT)
public class Coredump implements ConstantPoolHooks.Hook<MethodNode> {
	static {
		Tweaker.CONDITIONAL.annotatedMethod("roj/asmx/debug/api/Exceptional", new Coredump());
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
		c.computeFrames(Code.COMPUTE_FRAMES);

		var pars = node.parameters();
		for (int i = 0; i < pars.size(); i++) {
			Type type = pars.get(i);
			c.varLoad(type, base);
			base += type.length();
		}

		node.name(node.name()+"$exceptional");
		node.modifier = (char) (node.modifier & ~(ACC_PROTECTED|ACC_PUBLIC) | ACC_PRIVATE);

		Label start = c.label();
		c.invoke((node.modifier&ACC_STATIC) == 0 ? INVOKESPECIAL : INVOKESTATIC, node.owner(), node.name(), node.rawDesc());
		c.insn(node.returnType().getOpcode(IRETURN));
		Label end = c.label();

		c.visitExceptions();
		c.visitException(start, end, end, null);

		c.invokeS("roj/asmx/debug/Coredump", "__onExceptionCaptured", "(Ljava/lang/Throwable;)Ljava/lang/Throwable;");
		c.insn(ATHROW);

		c.finish();
		return true;
	}

	@ReferenceByGeneratedClass
	public static Throwable __onExceptionCaptured(Throwable ex) {
		System.out.println("异常已捕获");
		Coredump.heapdump();
		return ex;
	}

	public static void heapdump() {
		try {
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			ObjectName mbeanName = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
			server.invoke(mbeanName, "dumpHeap", new Object[]{"coredump_"+System.nanoTime()+".hprof", true}, new String[]{String.class.getName(), boolean.class.getName()});
			System.out.println("Heap dump created.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}