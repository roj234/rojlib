package roj.util.optimizer;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.text.logging.Logger;
import roj.util.ByteList;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/10/12 16:59
 */
public class Intrinsics {
	private static final Logger LOGGER = Logger.getLogger("Intrinsics");
	private static final Assembler assembler;
	private static final CodeInjector linker;
	static {
		CodeInjector tmp;
		try {
			var c = ClassNode.parseSkeleton(IOUtil.getResourceIL("roj/util/optimizer/JVMCI.class"));
			c.parent(Reflection.MAGIC_ACCESSOR_CLASS);
			tmp = (CodeInjector) Reflection.createInstance(Intrinsics.class.getClassLoader(), c);
		} catch (Throwable e) {
			if (e instanceof NoClassDefFoundError) {
				LOGGER.error("添加 -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI 虚拟机参数以启用LiteralASM");
			} else {
				LOGGER.error("Intrinsics初始化失败", e);
			}
			tmp = null;
		}
		linker = tmp;

		if (tmp != null) {
			assembler = Assembler.getInstance();

			if (!assembler.platform().equals("win64")) {
				System.out.println("[警告]截至目前,只有win64平台的汇编器经过了实际验证,其它平台可能无法正常使用");
			}
		} else {
			assembler = null;
		}
	}
	public static boolean available() {return linker != null;}

	public static boolean linkNative(@Nullable Object library, Class<?> caller) {
		if (linker == null || LibraryLoader.INSTANCE == null) return false;

		var nl = library instanceof String s ? LibraryLoader.INSTANCE.loadLibrary(caller, s) : library;

		for (Method method : caller.getDeclaredMethods()) {
			if ((method.getModifiers()&Opcodes.ACC_STATIC) != 0) {
				var jni = method.getAnnotation(IntrinsicCandidate.class);
				if (jni != null) linkSymbol(nl, method, jni.value(), jni.cdecl());
			}
		}

		return true;
	}

	private static void linkSymbol(Object library, Method method, String symbol, boolean cdecl) {
		if (symbol.isEmpty()) symbol = method.getName();
		long address = LibraryLoader.INSTANCE.find(library, symbol);
		if (address == 0) throw new UnsatisfiedLinkError("找不到符号(导出函数) "+symbol);

		var buf = new ByteList();
		if (cdecl) assembler.javaToCdecl(buf, method.getParameterTypes(), method.getParameterAnnotations());
		assembler.emitCall(buf, address);

		byte[] asm = buf.toByteArrayAndFree();
		try {
			linker.injectCode(method, asm, asm.length);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
