package roj.reflect;

import roj.ReferenceByGeneratedClass;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.compiler.plugins.asm.ASM.__asm;

/**
 * 在"不归路"上越走越远
 * @author Roj234
 * @since 2023/2/11 14:16
 */
final class VMInternals {
	@ReferenceByGeneratedClass
	private static final String PROP_NAME = "_ILJ9DC_", CLASS_NAME = "java/lang/🔓_IL🐟"; // "海阔凭鱼跃，天高任鸟飞"

	static final int JAVA_VERSION;
	static final Unsafe u;

	static {
		String v = System.getProperty("java.version");
		String major = v.substring(0, v.indexOf('.'));
		JAVA_VERSION = Integer.parseInt(major);

		Unsafe uu;
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			uu = (Unsafe) f.get(null);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			catchException(e);
			uu = null;
		}
		u = uu;

		if (JAVA_VERSION > 8) {
			if (JAVA_VERSION > 21) throw new UnsupportedOperationException("不支持Java22或更高版本，即便支持，在这些版本上，Bypass和其它反射工具的性能也会严重降低");
			try (var in = new DataInputStream(VMInternals.class.getClassLoader().getResourceAsStream("roj/reflect/Injector.class"))) {
				if (JAVA_VERSION >= 17) {
					Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
					_ImplLookup = (MethodHandles.Lookup) uu.getObject(uu.staticFieldBase(implLookup), uu.staticFieldOffset(implLookup));
				}

				defineClass(readClassData(in));
			} catch (Exception e) {
				catchException(e);
			}
		}
	}

	private static byte[] readClassData(DataInputStream in) throws IOException {
		byte[] b = new byte[2048];
		int off = 0;
		while (off < b.length) {
			int r = in.read(b, off, b.length - off);
			if (r < 0) {
				assert off == 1645 : "class size not match original";
				return Arrays.copyOf(b, off);
			}
			off += r;
		}
		return b;
	}

	private static MethodHandles.Lookup _ImplLookup;
	private static BiConsumer<Object, String> _ModuleOpener;
	private static Function<Object[], Class<?>> _ClassDefiner;

	private static void defineClass(byte[] bytes) {
		Class<?> type;
		try {
			if (JAVA_VERSION < 17) {
				type = DefineWeakClass("roj.reflect.VMInternals", bytes);
			} else {
				type = _ImplLookup.defineClass(bytes);
			}
		} catch (Exception e) {
			catchException(e);
			return;
		}
		InitializeClass(type);

		Object o = System.getProperties().remove(PROP_NAME);
		_ClassDefiner = Helpers.cast(o);
		_ModuleOpener = Helpers.cast(o);
	}

	// not removed still in java 21, but merge to one place for future refactor
	static void InitializeClass(Class<?> klass) { u.ensureClassInitialized(klass); }
	static String HackMagicAccessor() { return JAVA_VERSION <= 8 ? "sun/reflect/MagicAccessorImpl" : CLASS_NAME; }
	static void OpenModule(Class<?> src_module, String src_package, Class<?> target_module) { _ModuleOpener.accept(new Object[] {src_module, target_module}, src_package); }
	static void OpenModule(Module src_module, String src_package, Module target_module) { _ModuleOpener.accept(new Object[] {src_module, target_module}, src_package); }
	static Class<?> DefineWeakClass(String displayName, byte[] b) {
		if (JAVA_VERSION < 17) {
			if (__asm("""
				getstatic this u
				ldc sun.misc.Unsafe.class
				aload 1
				aconst_null
				invokevirtual java/lang/Unsafe defineAnonymousClass (Ljava/lang/Class;[B[Ljava/lang/Object;)Ljava/lang/Class;
				areturn
				""")) {
				try {
					Method m = Unsafe.class.getDeclaredMethod("defineAnonymousClass", Class.class, byte[].class, Object[].class);
					m.setAccessible(true);
					return (Class<?>) m.invoke(u, Unsafe.class, b, null);
				} catch (Exception e) {
					catchException(e);
				}
			}
		}
		/*
		 * Flags for Lookup.ClassOptions
		 */
		return _ClassDefiner.apply(new Object[]{VMInternals.class.getClassLoader(), VMInternals.class, displayName, b, 0, b.length, VMInternals.class.getProtectionDomain(), false, 2/*HIDDEN_CLASS*/|8/*ACCESS_VM_ANNOTATIONS*/, null});
	}

	private static void catchException(Throwable e) {
		String msg = JAVA_VERSION < 8 || JAVA_VERSION > 21 ? "您使用的JVM版本"+JAVA_VERSION+"不受支持!" : "未预料的内部错误";
		throw new InternalError(msg, e);
	}
}