package roj.reflect;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import roj.asm.AsmCache;
import roj.asm.ClassDefinition;
import roj.ci.annotation.IndirectReference;
import roj.ci.annotation.Public;
import roj.compiler.plugins.annotations.Attach;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.JVM;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static roj.reflect.Unsafe.U;

/**
 * @author Roj234
 * @since 2021/6/17 19:51
 */
public final class Reflection {
	static final sun.misc.Unsafe u;
	static final MethodHandles.Lookup IMPL_LOOKUP;

	public static final String PROP_NAME = "_ILJ9DC_", PROXY_CLASS = "java/lang/🔓_IL🐟";
	private static final BiConsumer<Object, String> openModule;
	static final Function<Object[], Class<?>> defineClass;

	//private static native MethodHandles.Lookup getImplLookup();
	static {
		try {
			Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			u = (sun.misc.Unsafe) f.get(null);

			byte[] bytes = readExact("roj/reflect/Reflection$.class", 1645);

			Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			IMPL_LOOKUP = (MethodHandles.Lookup) u.getObject(u.staticFieldBase(implLookup), u.staticFieldOffset(implLookup));

			if (JVM.VERSION > 21) {
				bytes[175] = 0x3F;
				bytes[1324] = 0x3F;
			}

			var type = IMPL_LOOKUP.defineClass(bytes);
			IMPL_LOOKUP.ensureInitialized(type);

			Object instance = System.getProperties().remove(PROP_NAME);
			defineClass = Helpers.cast(instance);
			openModule = Helpers.cast(instance);

			if (JVM.VERSION > 21) {
				killJigsaw(Reflection.class);
				System.err.println("WARNING: RojLib Java22+兼容模式：Jigsaw已为"+Reflection.class.getModule()+"禁用，请确保不会出现安全性问题！");
			}
		} catch (Exception e) {
			String msg = JVM.VERSION < 8 || JVM.VERSION > 21 ? "您使用的JVM版本"+ JVM.VERSION +"不受支持!" : "未预料的内部错误";
			throw new InternalError(msg, e);
		}
	}
	static byte[] readExact(String resource, int count) throws IOException {
		byte[] b = new byte[count];
		int off = 0;
		try (var in = Reflection.class.getClassLoader().getResourceAsStream(resource)) {
			if (in != null) {
				while (off < b.length) {
					int r = in.read(b, off, b.length - off);
					if (r < 0) throw new AssertionError("数据错误");
					off += r;
				}
				if (in.read() < 0) return b;
			}
		}
		throw new AssertionError("数据错误");
	}

	//region 类
	public static final String MAGIC_ACCESSOR_CLASS = JVM.VERSION > 21 ? "java/lang/Object" : PROXY_CLASS;

	private static final AtomicInteger ID = new AtomicInteger();
	public static int uniqueId() {return ID.getAndIncrement();}

	// Flags are from Lookup.ClassOptions
	public static final int
			NESTMATE_CLASS            = 0x00000001,
			HIDDEN_CLASS              = 0x00000002,
			STRONG_LOADER_LINK        = 0x00000004,
			ACCESS_VM_ANNOTATIONS     = 0x00000008;
	// also a 'Trusted' lookup for defineClass redirect
	@IndirectReference
	public static Class<?> defineClass(ClassLoader loader, Class<?> nestHost, String name, byte[] b, int off, int len, ProtectionDomain pd,
									   @MagicConstant(flags = {NESTMATE_CLASS, HIDDEN_CLASS, STRONG_LOADER_LINK, ACCESS_VM_ANNOTATIONS}) int flags) {
		if (loader == null) throw new NullPointerException("classLoader cannot be null");

		if (pd == null) pd = loader.getClass().getProtectionDomain();
		if (flags != 0 || TypeInternals.IMPL == null || nestHost != null) {
			if (flags == 0) flags = STRONG_LOADER_LINK;
			return defineClass.apply(new Object[]{loader, nestHost, name, b, off, len, pd, false, flags, null});
		} else {
			return TypeInternals.IMPL.defineClass(loader, name, b, off, len, pd);
		}
	}

	@ApiStatus.Internal
	public static Class<?> defineSystemClass(byte[] data) {
		ClassLoader caller = getCallerClass(3).getClassLoader();
		if (caller != Reflection.class.getClassLoader() && caller != null) throw new IllegalStateException("Not Safe "+caller);

		var loader = ClassLoader.getSystemClassLoader();
		return defineClass.apply(new Object[]{loader, Object.class, null, data, 0, data.length, loader.getClass().getProtectionDomain(), false, STRONG_LOADER_LINK, null});
	}

	/**
	 * 以{@code loader}创建一个类实例，不提供nestHost，它无法弱引用，同时JIT可能无法对其执行更佳的优化
	 */
	public static Object createInstance(ClassLoader loader, ClassDefinition node) {
		Class<?> klass = defineClass(loader, node);
		try {
			return U.allocateInstance(klass);
		} catch (Throwable e) {
			throw new IllegalStateException("类构造失败", e);
		}
	}
	public static Class<?> defineClass(ClassLoader loader, ClassDefinition node) {
		ByteList buf = AsmCache.toByteArrayShared(node);
		return TypeInternals.IMPL.defineClass(loader, null, buf.list, 0, buf.wIndex(), loader.getClass().getProtectionDomain());
	}

	public static Object createInstance(@NotNull Class<?> nestHost, ClassDefinition node) {return createInstance(nestHost, node, true);}
	/**
	 * 以{@code loader}创建一个名称为{@code name}的隐藏类实例
	 * @param privateAccess 允许定义的类访问{@code nestHost}中的所有private成员
	 */
	public static Object createInstance(@NotNull Class<?> nestHost, ClassDefinition node, boolean privateAccess) {
		var buf = AsmCache.toByteArrayShared(node);

		int flags = HIDDEN_CLASS | ACCESS_VM_ANNOTATIONS;
		if (privateAccess) flags |= NESTMATE_CLASS;

		ClassLoader classLoader = nestHost.getClassLoader();
		if (classLoader == null) classLoader = ClassLoader.getSystemClassLoader();
		Class<?> klass = defineClass(classLoader, nestHost, nestHost.getName()+"$R"/*+node.name()*/, buf.list, 0, buf.wIndex(), nestHost.getProtectionDomain(), flags);
		try {
			return U.allocateInstance(klass);
		} catch (Throwable e) {
			throw new IllegalStateException("类构造失败", e);
		}
	}

	/**
	 * 获取枚举的valueMap，用于valueOf而不触发异常.
	 * 请小心使用
	 * @param clazz 枚举类
	 * @return 它的值列表
	 */
	@Attach
	public static <T extends Enum<T>> @Unmodifiable Map<String, T> enumConstantDirectory(Class<T> clazz) {return TypeInternals.IMPL.enumConstantDirectory(clazz);}

	@Public
	private interface TypeInternals {
		TypeInternals IMPL = getInstance();
		private static TypeInternals getInstance() {
			AsmCache.clear();
			return Bypass.builder(TypeInternals.class)
					.delegate(ClassLoader.class, "defineClass")
					.delegate(Class.class, "enumConstantDirectory")
					.build();
		}

		Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd);
		<T extends Enum<T>> Map<String, T> enumConstantDirectory(Class<T> clazz);
	}
	//endregion
	//region 模块
	/**
	 * 对target_module开放src_module中的src_package
	 * src_module和target_module的类型是[Class|Module]
	 */
	private static void openModule(Class<?> src_module, String src_package, Class<?> target_module) {
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkOpenModule(src_module, src_package, target_module);

		openModule.accept(new Object[] {src_module, target_module}, src_package);
	}
	/**
	 * 禁用模块权限系统
	 */
	public static void killJigsaw(Class<?> target_module) {
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkKillJigsaw(target_module);

		for (Module module : Object.class.getModule().getLayer().modules()) {
			for (String pkg : module.getDescriptor().packages()) {
				openModule.accept(new Object[] {module, target_module}, pkg);
			}
		}
	}
	//endregion
	//region 堆栈
	private static final StackWalker WALKER = StackWalker.getInstance(EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), 5);
	public static Class<?> getCallerClass(int backward) {return getCallerClass(backward+1, null);}
	public static Class<?> getCallerClass(int backward, Class<?> skipIf) {
		return WALKER.walk(stream -> {
			var frame = stream.skip(backward).filter(frame1 -> frame1.getDeclaringClass() != skipIf).findFirst().orElse(null);
			return frame == null ? null : frame.getDeclaringClass();
		});
	}

	/**
	 * 获取方法调用者的实例，如果对应方法是静态的，那么结果是未定义的
	 *
	 * @param skip 往回数的栈帧数目
	 * @return 调用者的实例
	 * @since 2024/6/4 6:09
	 */
	public static Object getCallerInstance(int skip) {
		return CalleeInternals.LIVE.walk(stream -> {
			var frame = stream.skip(skip).findFirst().orElse(null);
			return frame == null ? null : ((Object[]) U.getReference(frame, CalleeInternals.LOCALS))[0];
		});
	}
	private static final class CalleeInternals {
		static final long LOCALS = Unsafe.objectFieldOffset(Telescope.findClass("java.lang.LiveStackFrameInfo"), "locals", Object[].class);
		static final StackWalker LIVE;

		static {
			// simple java.lang.LiveStackFrame.getStackWalker() without SM checks
			var optionType = Telescope.findClass("java.lang.StackWalker$ExtendedOption");
			Telescope lookup = Telescope.trustedLookup();
			Object localsAndOperandsField = lookup.findStatic(optionType, "LOCALS_AND_OPERANDS", optionType);
			var localsAndOperands = U.getReference(lookup.staticFieldBase(localsAndOperandsField), lookup.staticFieldOffset(localsAndOperandsField));
			BiFunction<Object, Object, StackWalker> fn = Helpers.cast(Bypass.builder(BiFunction.class).delegate_o(StackWalker.class, "newInstance", "apply").build());
			LIVE = fn.apply(Collections.emptySet(), localsAndOperands);
		}
	}
	//endregion
}