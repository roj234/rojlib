package roj.test;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.visitor.CodeWriter;
import roj.dev.HRAgent;
import roj.dev.hr.HRContext;
import roj.io.IOUtil;
import roj.ui.CLIUtil;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.locks.LockSupport;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2023/10/15 0015 17:23
 */
public class HotReloadTest {
	public static class A {
		int myField;

		A() {}

		void ada() {
			System.out.println("正在执行A.ada() "+myField++);
		}

		static int genfunc0(int arg0) {
			for (int i = 0; i < 100; i++) {
				if (arg0 > 0) {
					int myVar2 = 1;
				}
			}
			return 233;
		}
	}

	public static void main(String[] args) throws Exception {
		Instrumentation inst = HRAgent.getInstInst();
		HRContext context = new HRContext(HotReloadTest.class.getClassLoader());

		String path = "roj/dev/hr/Test$A.class";
		byte[] bytes = IOUtil.getResource(path);
		ConstantData data2 = Parser.parse(bytes);
		context.update(data2);
		context.commit(inst);

		runThreadA();

		System.out.println("wait");

		CLIUtil.readString("");

		data2.parsed();
		data2.methods.clear();
		CodeWriter c = data2.newMethod(ACC_PUBLIC, "<init>", "()V");
		c.visitSize(2,1);
		c.one(ALOAD_0);
		c.invokeD("java/lang/Object", "<init>", "()V");
		c.field(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		c.ldc("哈哈哈哈，我又被替换了！");
		c.invokeV("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
		c.one(RETURN);

		c = data2.newMethod(ACC_PUBLIC, "added", "()V");
		c.visitSize(2,1);
		c.field(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		c.ldc("这是新增的！");
		c.invokeV("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
		c.one(RETURN);
		context.update(data2);


		context.commit(inst);
		System.out.println("post");
		Thread.sleep(5000);
	}

	private static void runThreadA() {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				A a = new A();
				while (true) {
					a.ada();
					LockSupport.parkNanos(1000000000L);
				}
			}
		});
		t.setDaemon(true);
		t.setName("A");
		t.start();
	}
}