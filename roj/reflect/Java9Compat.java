package roj.reflect;

import roj.NativeLibrary;
import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cst.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.util.ByteList;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.asm.Opcodes.*;

/**
 * 这哪里是兼容JVM，明明是JVM被兼容
 * @author Roj234
 * @since 2023/2/11 0011 14:16
 */
public final class Java9Compat {
	static {
		if (ReflectionUtils.JAVA_VERSION > 8) {
			AsmShared.local().setLevel(true);
			try {
				DefineClassHandle1();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				AsmShared.local().setLevel(false);
			}
		}
	}

	/**
	 * 获取类定义者
	 * 输入的对象: String name, byte[] buf, int off, int len, ClassLoader cl, ProtectionDomain pd
	 */
	public static Function<Object[], Class<?>> DefineClassHandle() {
		return Java9DefineClass;
	}

	private static Function<Object[], Class<?>> Java9DefineClass;
	public static void DefineClassHandle1() {
		Class<?> jdkInternal;
		try {
			jdkInternal = Unsafe.class.getDeclaredField("theInternalUnsafe").getType();
		} catch (NoSuchFieldException e) {
			throw new UnsupportedOperationException("Cannot find 'theInternalUnsafe'");
		}

		String unsafeKlass = jdkInternal.getName().replace('.', '/');

		ConstantData data = new ConstantData();
		data.name("jdk/internal/misc/IL☠");
		data.interfaces().add("java/util/function/Function");
		data.newField(AccessFlag.PRIVATE|AccessFlag.STATIC|AccessFlag.FINAL, "theInternalUnsafe", new Type(unsafeKlass));

		CodeWriter w = data.newMethod(AccessFlag.PUBLIC|AccessFlag.STATIC, "<clinit>", "()V");
		w.visitSize(2, 0);
		w.invoke(INVOKESTATIC, "jdk/internal/misc/Unsafe", "getUnsafe", "()Ljdk/internal/misc/Unsafe;");
		w.field(PUTSTATIC, data, 0);
		w.invoke(INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;");
		w.ldc(new CstString("_ILJ9DC_"));
		w.newObject(data.name);
		w.invoke(INVOKEVIRTUAL, "java/util/Properties", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
		w.one(RETURN);
		w.finish();

		data.npConstructor();

		w = data.newMethod(AccessFlag.PUBLIC, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
		w.visitSize(7, 2);
		w.field(GETSTATIC, data, 0);
		w.one(ALOAD_1);
		w.clazz(CHECKCAST, "[Ljava/lang/Object;");
		w.one(ASTORE_1);
		w.unpackArray(1, String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class);
		w.invoke(INVOKEVIRTUAL, unsafeKlass, "defineClass", "(Ljava/lang/String;[BIILjava/lang/ClassLoader;Ljava/security/ProtectionDomain;)Ljava/lang/Class;");
		w.one(ARETURN);
		w.finish();

		Class<?> 乌拉;
		j17:
		if (ReflectionUtils.JAVA_VERSION < 17) {
			乌拉 = FieldAccessor.u.defineAnonymousClass(jdkInternal, Parser.toByteArray(data), null);
		} else {
			byte[] bytes = Parser.toByteArray(data);
			try {
				// noinspection all
				Method m = ClassLoader.class.getDeclaredMethod("defineClass1", ClassLoader.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class);

				m.setAccessible(true);
				乌拉 = (Class<?>) m.invoke(null, null, data.name, bytes, 0, bytes.length, null, null);
			} catch (Exception e) {
				if (NativeLibrary.loaded) {
					try {
						乌拉 = nDefineClass(data.name, bytes, 0, bytes.length, null, null);
						break j17;
					} catch (Throwable ignored) {}
				}
				new Error("添加 --add-opens=java.base/java.lang=ALL-UNNAMED", e).printStackTrace();
				System.exit(1);
				return;
			}
		}
		FieldAccessor.u.ensureClassInitialized(乌拉);

		System.out.println("[Java9Compat]"+乌拉.getName()+"注入成功");

		Java9DefineClass = Helpers.cast(System.getProperties().remove("_ILJ9DC_"));
	}

	private static native Class<?> nDefineClass(String name, byte[] b, int off, int len, ProtectionDomain pd, String source);

	private static Class<?> Java9OpenMagic;
	public synchronized static String HackMagicAccessor() {
		if (ReflectionUtils.JAVA_VERSION <= 8) return "sun/reflect/MagicAccessorImpl";

		final String name = "java/util/IL🐎";

		if (Java9OpenMagic == null) {
			ConstantData 马 = new ConstantData();
			马.name(name);
			马.parent("jdk/internal/reflect/MagicAccessorImpl");

			CodeWriter cw = 马.newMethod(AccessFlag.PUBLIC|AccessFlag.STATIC, "<clinit>", "()V");
			cw.visitSize(1, 0);
			cw.one(RETURN);
			cw.finish();

			马.npConstructor();

			ByteList buf = Parser.toByteArrayShared(马);
			Java9OpenMagic = DefineClassHandle().apply(new Object[]{
				马.name.replace('/', '.'), buf.list, 0, buf.wIndex(), null, null
			});

			System.out.println("[Java9Compat]"+Java9OpenMagic.getName());
		}

		return name;
	}

	private static BiConsumer<Object, String> Java9ModuleOpener;
	/**
	 * 获取模块开放者
	 * 第一个参数为Object[2] {
	 *     source, target
	 * }
	 * 第二个参数是source的包
	 */
	public synchronized static BiConsumer<Object, String> ModuleOpener() {
		if (Java9ModuleOpener != null) return Java9ModuleOpener;

		Function<Object[], Class<?>> definer = Java9DefineClass;

		ConstantData data = new ConstantData();
		data.name("java/lang/IL🐟");
		data.interfaces().add("java/util/function/BiConsumer");
		data.version = 53<<16;

		CodeWriter cw = data.newMethod(AccessFlag.PUBLIC|AccessFlag.STATIC, "<clinit>", "()V");
		cw.visitSize(4, 0);
		cw.invoke(INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;");
		cw.ldc(new CstString("_ILJ9OM_"));
		cw.newObject(data.name);
		cw.invoke(INVOKEVIRTUAL, "java/util/Properties", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
		cw.one(RETURN);
		cw.finish();

		data.npConstructor();

		cw = data.newMethod(AccessFlag.PUBLIC, "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V");
		cw.visitSize(3, 3);

		cw.one(ALOAD_1);
		cw.clazz(CHECKCAST, "[Ljava/lang/Object;");
		cw.one(ASTORE_0);

		cw.one(ALOAD_0);
		cw.one(ICONST_0);
		cw.one(AALOAD);
		cw.clazz(CHECKCAST, "java/lang/Module");
		cw.one(ASTORE_1);

		cw.one(ALOAD_0);
		cw.one(ICONST_1);
		cw.one(AALOAD);
		cw.clazz(CHECKCAST, "java/lang/Module");
		cw.one(ASTORE_0);

		cw.one(ALOAD_2);
		cw.clazz(CHECKCAST, "java/lang/String");
		cw.one(ASTORE_2);

		Label label = CodeWriter.newLabel();
		cw.one(ALOAD_0);
		cw.jump(IFNONNULL, label);

		cw.one(ALOAD_1);
		cw.one(ALOAD_2);
		cw.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddExportsToAllUnnamed", "(Ljava/lang/String;)V");

		cw.one(ALOAD_1);
		cw.one(ALOAD_2);
		cw.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddOpensToAllUnnamed", "(Ljava/lang/String;)V");

		cw.one(RETURN);
		cw.label(label);

		cw.one(ALOAD_1);
		cw.one(ALOAD_2);
		cw.one(ALOAD_0);
		cw.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddExports", "(Ljava/lang/String;Ljava/lang/Module;)V");

		cw.one(ALOAD_1);
		cw.one(ALOAD_2);
		cw.one(ALOAD_0);
		cw.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddOpens", "(Ljava/lang/String;Ljava/lang/Module;)V");

		cw.one(RETURN);
		cw.finish();

		ByteList buf = Parser.toByteArrayShared(data);
		Class<?> cls = definer.apply(new Object[] {data.name.replace('/', '.'), buf.list, 0, buf.wIndex(), null, null});
		FieldAccessor.u.ensureClassInitialized(cls);

		System.out.println("[Java9Compat]OpenModule加载成功");

		return Java9ModuleOpener = Helpers.cast(System.getProperties().remove("_ILJ9OM_"));
	}

	public static Object getModule(Class<?> clazz) {
		try {
			// noinspection all
			return Class.class.getDeclaredMethod("getModule").invoke(clazz);
		} catch (Exception e) {
			return null;
		}
	}
}
