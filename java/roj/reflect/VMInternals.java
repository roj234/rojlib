package roj.reflect;

import roj.ReferenceByGeneratedClass;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.compiler.plugins.asm.ASM;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.reflect.ReflectionUtils.JAVA_VERSION;

/**
 * 在"不归路"上越走越远
 * @author Roj234
 * @since 2023/2/11 14:16
 */
final class VMInternals {
	@ReferenceByGeneratedClass
	private static final String PROP_NAME = "_ILJ9DC_", CLASS_NAME = "java/lang/🔓_IL🐟"; // "海阔凭鱼跃，天高任鸟飞"

	static final Unsafe u;

	static {
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
			try (var in = VMInternals.class.getClassLoader().getResourceAsStream("roj/reflect/Fish")) {
				if (JAVA_VERSION >= 17) {
					Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
					_ImplLookup = (MethodHandles.Lookup) uu.getObject(uu.staticFieldBase(implLookup), uu.staticFieldOffset(implLookup));
				}

				defineClass(readClassData(in));

				if (JAVA_VERSION > 21) {
					ReflectionUtils.killJigsaw(VMInternals.class);
					System.out.println("[RojLib Warning] 模块系统已为RojLib模块禁用，这会造成严重的安全问题，请确保为RojLib使用了独立的模块！");
				}
			} catch (Exception e) {
				catchException(e);
			}
		}
	}

	private static byte[] readClassData(InputStream in) throws IOException {
		byte[] b = new byte[1646];
		int off = 0;
		while (off < b.length) {
			int r = in.read(b, off, b.length - off);
			if (r < 0) {
				assert off == 1645 : "data corrupt";
				return Arrays.copyOf(b, off);
			}
			off += r;
		}
		return b;
	}

	static MethodHandles.Lookup _ImplLookup;
	private static BiConsumer<Object, String> _ModuleOpener;
	private static Function<Object[], Class<?>> _ClassDefiner;

	private static void defineClass(byte[] bytes) {
		Class<?> type;
		try {
			if (JAVA_VERSION < 17) {
				type = DefineWeakClass("roj.reflect.VMInternals", bytes);
			} else {
				if (JAVA_VERSION > 21) {
					bytes[175] = 0x3F;
					bytes[1324] = 0x3F;
				}
				type = _ImplLookup.defineClass(bytes);
			}

			if (JAVA_VERSION > 21) _ImplLookup.ensureInitialized(type);
			else u.ensureClassInitialized(type);
		} catch (Exception e) {
			catchException(e);
			return;
		}

		Object o = System.getProperties().remove(PROP_NAME);
		_ClassDefiner = Helpers.cast(o);
		_ModuleOpener = Helpers.cast(o);
	}

	static String HackMagicAccessor() { return JAVA_VERSION > 21 ? "java/lang/Object" : JAVA_VERSION <= 8 ? "sun/reflect/MagicAccessorImpl" : CLASS_NAME; }
	static void OpenModule(Class<?> src_module, String src_package, Class<?> target_module) { _ModuleOpener.accept(new Object[] {src_module, target_module}, src_package); }
	static void OpenModule(Module src_module, String src_package, Module target_module) { _ModuleOpener.accept(new Object[] {src_module, target_module}, src_package); }
	static Class<?> DefineWeakClass(String displayName, byte[] b) {
		if (JAVA_VERSION < 17) {
			ASM.asm(mw -> {
				mw.field(Opcodes.GETSTATIC, "roj/reflect/VMInternals", "u", "Ljava/lang/Unsafe;");
				mw.ldc(new CstClass("sun/misc/Unsafe"));
				mw.one(Opcodes.ALOAD_1);
				mw.one(Opcodes.ACONST_NULL);
				mw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/Unsafe", "defineAnonymousClass", "(Ljava/lang/Class;[B[Ljava/lang/Object;)Ljava/lang/Class");
				mw.one(Opcodes.ARETURN);
			});
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