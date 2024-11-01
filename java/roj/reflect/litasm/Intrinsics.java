package roj.reflect.litasm;

import org.jetbrains.annotations.Nullable;
import roj.RojLib;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.reflect.ClassDefiner;
import roj.reflect.ReflectionUtils;
import roj.reflect.litasm.internal.JVMCI;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.util.ByteList;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/10/12 0012 16:59
 */
public class Intrinsics {
	private static final Logger LOGGER = Logger.getLogger("LitASM");
	private static final Assembler assembler = Assembler.getInstance();
	private static final CodeInjector linker;
	static {
		if (!assembler.platform().equals("win64")) {
			System.out.println("[警告]截至目前,只有win64平台的汇编器经过了实际验证,其它平台可能无法正常使用");
		}

		CodeInjector tmp;
		try {
			var c = Parser.parseConstants(IOUtil.getResource("roj/reflect/litasm/internal/JVMCI.class"));
			c.parent(Bypass.MAGIC_ACCESSOR_CLASS);
			ClassDefiner.defineGlobalClass(c);
			tmp = new JVMCI();
		} catch (Throwable e) {
			if (RojLib.hasNative(RojLib.FastJNI)) tmp = new NativeInjector();
			else {
				if (e instanceof NoClassDefFoundError) {
					LOGGER.error("添加 -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI 虚拟机参数以启用LiteralASM");
				} else {
					LOGGER.error("LiteralAsm初始化失败", e);
				}
				tmp = null;
			}
		}
		linker = tmp;
	}
	public static boolean available() {return linker != null;}
	public static boolean linkNative(@Nullable String library) {return linkNative(library, ReflectionUtils.getCallerClass(2));}
	public static boolean linkNative(@Nullable Object library, Class<?> caller) {
		if (linker == null || LibraryLoader.INSTANCE == null) return false;

		var nl = library instanceof String s ? LibraryLoader.INSTANCE.loadLibrary(caller, s) : library;

		for (Method method : caller.getDeclaredMethods()) {
			if ((method.getModifiers()&Opcodes.ACC_STATIC) != 0) {
				var asm = method.getAnnotation(InlineASM.class);
				if (asm != null && linkOpcode(method, asm.value(), asm.platform())) {
					continue;
				}

				var jni = method.getAnnotation(FastJNI.class);
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

	private static boolean linkOpcode(Method method, String[] opcode, String[] platform) {
		for (int i = 0; i < platform.length; i++) {
			if (!platform[i].equals(assembler.platform())) continue;

			byte[] asm = TextUtil.hex2bytes(opcode[i]);
			try {
				linker.injectCode(method, asm, asm.length);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}
