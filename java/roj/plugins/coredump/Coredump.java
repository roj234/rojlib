package roj.plugins.coredump;

import roj.ReferenceByGeneratedClass;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.insn.AttrCode;
import roj.asm.insn.Label;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.NodeTransformer;
import roj.asmx.TransformException;
import roj.asmx.launcher.Autoload;
import roj.asmx.launcher.DefaultTweaker;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/6/30 0030 19:15
 */
@Autoload(Autoload.Target.INIT)
public class Coredump implements NodeTransformer<MethodNode> {
	static {
		DefaultTweaker.CONDITIONAL.annotatedMethod("roj/plugins/coredump/Exceptional", new Coredump());
	}

	@Override
	public boolean transform(ClassNode cls, MethodNode it) throws TransformException {
		var c = cls.newMethod(it.modifier, it.name(), it.rawDesc());

		int base;
		int size = TypeHelper.paramSize(it.rawDesc());
		if ((it.modifier&ACC_STATIC) == 0) {
			size++;
			base = 1;
			c.insn(ALOAD_0);
		} else {
			base = 0;
		}
		if (!it.rawDesc().endsWith(")V") && size == 0) {
			size = 1;
		}
		c.visitSize(size, size);
		c.computeFrames(AttrCode.COMPUTE_FRAMES);

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
		c.insn(it.returnType().shiftedOpcode(IRETURN));
		Label end = c.label();

		c.visitExceptions();
		c.visitException(start, end, end, null);

		c.invokeS("roj/plugins/coredump/Coredump", "__onExceptionCaptured", "(Ljava/lang/Throwable;)Ljava/lang/Throwable;");
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