package roj.reflect;

import roj.NativeLibrary;
import roj.ReferenceByPrecompiledClass;
import roj.asm.Parser;
import roj.asm.cp.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.asm.Opcodes.*;
import static roj.reflect.ReflectionUtils.u;

/**
 * 这哪里是兼容JVM，明明是JVM被兼容
 * @author Roj234
 * @since 2023/2/11 0011 14:16
 */
public final class Java9Compat {
	@ReferenceByPrecompiledClass
	private static final String PROP_NAME = "_ILJ9DC_", CLASS_NAME = "java/lang/🔓_IL🐟"; // "海阔凭鱼跃，天高任鸟飞"

	public static void main(String[] args) throws Exception {
		ConstantData ILCD = new ConstantData();
		ILCD.name(CLASS_NAME);
		ILCD.interfaces().add("java/util/function/Function");
		ILCD.interfaces().add("java/util/function/BiConsumer");
		ILCD.parent("jdk/internal/reflect/MagicAccessorImpl");
		ILCD.newField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "theInternalUnsafe", new Type("jdk/internal/misc/Unsafe"));
		ILCD.npConstructor();

		CodeWriter w = ILCD.newMethod(ACC_PUBLIC | ACC_STATIC, "<clinit>", "()V");
		w.visitSize(2, 0);
		w.invoke(INVOKESTATIC, "jdk/internal/misc/Unsafe", "getUnsafe", "()Ljdk/internal/misc/Unsafe;");
		w.field(PUTSTATIC, ILCD, 0);
		w.invoke(INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;");
		w.ldc(new CstString(PROP_NAME));
		w.newObject(ILCD.name);
		w.invoke(INVOKEVIRTUAL, "java/util/Properties", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
		w.one(RETURN);
		w.finish();

		w = ILCD.newMethod(ACC_PUBLIC, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
		w.visitSize(7, 2);
		w.one(ALOAD_1);
		w.clazz(CHECKCAST, "[Ljava/lang/Object;");
		w.one(ASTORE_0);
		w.unpackArray(0, ClassLoader.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class);
		w.invoke(INVOKEVIRTUAL, "jdk/internal/misc/Unsafe", "defineClass", "(Ljava/lang/String;[BIILjava/lang/ClassLoader;Ljava/security/ProtectionDomain;)Ljava/lang/Class;");
		w.one(ARETURN);
		w.finish();

		// source target package
		w = ILCD.newMethod(ACC_PUBLIC, "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V");
		w.visitSize(3, 3);

		w.one(ALOAD_1);
		w.clazz(CHECKCAST, "[Ljava/lang/Object;");
		w.one(ASTORE_0);

		w.one(ALOAD_0);
		w.one(ICONST_0);
		w.one(AALOAD);
		w.clazz(CHECKCAST, "java/lang/Class");
		w.invoke(INVOKEVIRTUAL, "java/lang/Class", "getModule", "()Ljava/lang/Module;");
		w.one(ASTORE_1);

		w.one(ALOAD_0);
		w.one(ICONST_1);
		w.one(AALOAD);
		w.clazz(CHECKCAST, "java/lang/Class");
		w.invoke(INVOKEVIRTUAL, "java/lang/Class", "getModule", "()Ljava/lang/Module;");
		w.one(ASTORE_0);

		w.one(ALOAD_2);
		w.clazz(CHECKCAST, "java/lang/String");
		w.one(ASTORE_2);

		Label label = CodeWriter.newLabel();
		w.one(ALOAD_0);
		w.jump(IFNONNULL, label);

		w.one(ALOAD_1);
		w.one(ALOAD_2);
		w.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddExportsToAllUnnamed", "(Ljava/lang/String;)V");

		w.one(ALOAD_1);
		w.one(ALOAD_2);
		w.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddOpensToAllUnnamed", "(Ljava/lang/String;)V");

		w.one(RETURN);
		w.label(label);

		w.one(ALOAD_1);
		w.one(ALOAD_2);
		w.one(ALOAD_0);
		w.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddExports", "(Ljava/lang/String;Ljava/lang/Module;)V");

		w.one(ALOAD_1);
		w.one(ALOAD_2);
		w.one(ALOAD_0);
		w.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddOpens", "(Ljava/lang/String;Ljava/lang/Module;)V");

		w.one(RETURN);
		w.finish();

		DataOutputStream fos = new DataOutputStream(new FileOutputStream("ReflectionCompat.class"));
		Parser.toByteArrayShared(ILCD).writeToStream(fos);
		fos.close();
	}

	static {
		if (ReflectionUtils.JAVA_VERSION > 8) {
			try (DataInputStream in = new DataInputStream(Java9Compat.class.getResourceAsStream("/META-INF/ReflectionCompat.class"))) {
				defineClass(readClassData(in));
				System.out.println("[Java9Compat]加载成功");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static byte[] readClassData(DataInputStream in) throws IOException {
		byte[] b = new byte[2048];
		int off = 0;
		while (off < b.length) {
			int r = in.read(b, off, b.length - off);
			if (r < 0) {
				assert off == 1478 : "class size not match original";
				return Arrays.copyOf(b, off);
			}
			off += r;
		}
		return b;
	}

	private static String Java9OpenMagic;
	private static BiConsumer<Object, String> Java9ModuleOpener;
	private static Function<Object[], Class<?>> Java9DefineClass;

	public static void defineClass(byte[] bytes) {
		Class<?> jdkInternal;
		try {
			jdkInternal = Unsafe.class.getDeclaredField("theInternalUnsafe").getType();
		} catch (NoSuchFieldException e) {
			throw new UnsupportedOperationException("Cannot find 'theInternalUnsafe'");
		}

		Class<?> type;
		j17:
		if (ReflectionUtils.JAVA_VERSION < 17) {
			//type = u.defineAnonymousClass(jdkInternal, bytes, null);
			try {
				Method m = Unsafe.class.getDeclaredMethod("defineAnonymousClass", Class.class, byte[].class, Object[].class);
				m.setAccessible(true);
				type = (Class<?>) m.invoke(u, jdkInternal, bytes, null);
			} catch (Exception e) {
				throw new InternalError("此异常不应出现,不过由于该项目已全面升级到Java17...", e);
			}
		} else {
			if (NativeLibrary.loaded) {
				type = defineClass0(null, null, bytes, bytes.length);
				break j17;
			}

			// todo find (but probably cannot since modules) a way to define my java.lang hacker
			try {
				// noinspection all
				Method m = ClassLoader.class.getDeclaredMethod("defineClass1", ClassLoader.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class);
				m.setAccessible(true);
				type = (Class<?>) m.invoke(null, null, null, bytes, 0, bytes.length, null, null);
			} catch (Exception e) {
				throw new InternalError("Java9Compat需要您添加虚拟机参数 '--add-opens=java.base/java.lang=ALL-UNNAMED' 来初始化", e);
			}
		}

		u.ensureClassInitialized(type);

		Object o = System.getProperties().remove(PROP_NAME);

		Java9OpenMagic = type.getName().replace('.', '/');
		Java9DefineClass = Helpers.cast(o);
		Java9ModuleOpener = Helpers.cast(o);
	}

	private static native Class<?> defineClass0(String name, ClassLoader cl, byte[] b, int len);

	// 卡拉赞 (所以没意义)
	public static Class<?> DefineAnyClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd, String source) {
		return Java9DefineClass.apply(new Object[]{loader,name,b,off,len,pd,source});
	}

	public static String HackMagicAccessor() { return ReflectionUtils.JAVA_VERSION <= 8 ? "sun/reflect/MagicAccessorImpl" : Java9OpenMagic; }

	@Deprecated
	public static BiConsumer<Object, String> ModuleOpener() { return Java9ModuleOpener; }
	public static void OpenModule(Class<?> src_module, String src_package, Class<?> target_module) {
		Java9ModuleOpener.accept(new Object[] {src_module, target_module}, src_package);
	}
}