package roj.plugin.di;

import roj.ReferenceByGeneratedClass;
import roj.archive.zip.ZipFile;
import roj.asm.ClassNode;
import roj.asm.Member;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.type.Type;
import roj.asmx.AnnotatedElement;
import roj.asmx.AnnotationRepo;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.TransformUtil;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.concurrent.Flow;
import roj.io.FastFailException;
import roj.plugin.Panger;
import roj.plugin.PluginClassLoader;
import roj.plugin.PluginDescriptor;
import roj.reflect.Reflection;
import roj.reflect.VirtualReference;
import roj.util.Helpers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 依赖注入管理器
 * 实例：依赖注入上下文容器，在依赖提供和清理阶段传递注入点元信息。
 *
 * @param plugin   依赖所属的插件实例
 * @param owner    声明{@link Autowired}字段的宿主类
 * @param fieldName 字段名称，较少使用
 * @since 2025/06/20 19:54
 */
public record DIContext(
		PluginDescriptor plugin,
		Class<?> owner,
		String fieldName
) {

	private static final VirtualReference<Map<Class<?>, Object[]>> dependencyProviders = new VirtualReference<>();
	private static final Map<PluginDescriptor, List<Object>> resources = new HashMap<>();

	@ReferenceByGeneratedClass
	public static DIContext _CTX(Class<?> caller, String fieldName) {
		PluginDescriptor plugin = Objects.requireNonNull(Panger.getPluginManager().getOwner(caller));
		return new DIContext(plugin, caller, fieldName);
	}

	@ReferenceByGeneratedClass
	public static void _UCB(Object instance, DIContext ctx, Class<?> formalType) {
		List<Object> objects = resources.computeIfAbsent(ctx.plugin, Helpers.fnArrayList());
		objects.add(formalType);
		objects.add(instance);
	}

	/**
	 * 在静态初始化块内注册
	 * @param type
	 * @param instance
	 * @param <T>
	 */
	public static <T> void registerUnloadCallback(Class<T> type, BiConsumer<PluginDescriptor, T> instance) {
		try {
			dependencyProviders.getEntry(type.getClassLoader()).getValue().get(type)[1] = instance;
		} catch (NullPointerException npe) {
			throw new IllegalStateException("应在DependencyProvider注解所属类的静态初始化块内注册清理函数");
		}
	}

	public static void onPluginLoaded(PluginDescriptor plugin) {
		ZipFile archive = plugin.getArchive();
		if (archive != null) {
			AnnotationRepo repo = new AnnotationRepo();
			try {
				repo.addOptionalCache(archive);
			} catch (IOException e) {
				e.printStackTrace();
			}

			Set<AnnotatedElement> dependency = repo.annotatedBy("roj/plugin/di/DependencyProvider");
			if (!dependency.isEmpty()) dependencyLoad(plugin.getClassLoader(), dependency);

			Set<AnnotatedElement> autowired = repo.annotatedBy("roj/plugin/di/Autowired");
			if (!autowired.isEmpty()) dependencyInjection(plugin, autowired);
		}
	}

	@SuppressWarnings("unchecked")
	public static void onPluginUnload(PluginDescriptor descriptor) {
		List<Object> objects = DIContext.resources.remove(descriptor);
		if (objects != null) {
			for (int i = 0; i < objects.size(); i += 2) {
				Class<?> formalType = (Class<?>)objects.get(i);
				Object instance = objects.get(i+1);

				var cleanup = (BiConsumer<PluginDescriptor, Object>) dependencyProviders.getEntry(formalType.getClassLoader()).getValue().get(formalType)[1];
				if (cleanup != null) {
					cleanup.accept(descriptor, instance);
				}
			}
		}
	}

	public static void dependencyLoad(AnnotationRepo repo) {
		dependencyLoad(DIContext.class.getClassLoader(), repo.annotatedBy("roj/plugin/di/DependencyProvider"));
	}

	private static void dependencyLoad(ClassLoader classLoader, Set<AnnotatedElement> dependency) {
		for (AnnotatedElement element : dependency) {
			try {
				Class<?> injectOwner = Class.forName(element.owner().replace('/', '.'), false, classLoader);
				List<Type> types = Type.methodDesc(((Member) element.node()).rawDesc());
				Class<?> injectType = Class.forName(types.get(types.size() - 1).owner.replace('/', '.'), false, classLoader);

				Object[] objects = dependencyProviders.computeIfAbsent(injectType.getClassLoader(), Helpers.fnHashMap()).putIfAbsent(injectType, new Object[]{element, null});
				if (objects != null) throw new IllegalStateException("priority暂未实现");

				Reflection.ensureClassInitialized(injectOwner);
			} catch (ClassNotFoundException|NoClassDefFoundError e) {
				throw new FastFailException("无法加载依赖的类，可能是权限问题", e);
			}
		}
	}

	private static void dependencyInjection(PluginDescriptor plugin, Set<AnnotatedElement> autowired) {
		Map<String, ArrayList<AnnotatedElement>> groupBy = Flow.of(autowired).groupBy(AnnotatedElement::owner, ArrayList::new, (list, value) -> {
			list.add(value);
			return list;
		});

		PluginClassLoader classLoader = plugin.getClassLoader();

		ConstantPoolHooks.Hook<ClassNode> classNodeHook = (ctx, node) -> {
			TransformUtil.prependStaticConstructor(ctx, cw -> {
				for (AnnotatedElement element : groupBy.get(ctx.name())) {
					// create context
					cw.ldc(new CstClass(ctx.name()));
					cw.ldc(element.name());
					cw.invokeS("roj/plugin/di/DIContext", "_CTX", "(Ljava/lang/Class;Ljava/lang/String;)Lroj/plugin/di/DIContext;");
					cw.insn(Opcodes.ASTORE_0);

					String typeName = Type.fieldDesc(element.desc()).owner;
					AnnotatedElement method;
					boolean hasCleanup;
					try {
						Class<?> type = Class.forName(typeName.replace('/', '.'), false, classLoader);
						var entry = dependencyProviders.getEntry(type.getClassLoader());
						Object[] typeDef;
						if (entry == null || (typeDef = entry.getValue().get(type)) == null)
							throw new FastFailException("类型"+typeName+"未通过DependencyProvider注册");

						method = (AnnotatedElement) typeDef[0];
						hasCleanup = typeDef[1] != null;
					} catch (ClassNotFoundException|NoClassDefFoundError e) {
						throw new FastFailException("无法加载依赖的类，可能是权限问题", e);
					}

					if (method.desc().startsWith("(Lroj/plugin/di/DIContext;"))
						cw.insn(Opcodes.ALOAD_0);
					if (method.annotations().get("roj/plugin/di/DependencyProvider").getBool("allowQualifier"))
						cw.ldc(element.annotations().get("roj/plugin/di/Autowired").getString("qualifier", null));

					// create object
					cw.invokeS(method.owner(), method.name(), method.desc());

					if (hasCleanup) cw.insn(Opcodes.DUP);

					cw.field(Opcodes.PUTSTATIC, ctx.name(), element.name(), element.desc());

					if (hasCleanup) {
						cw.insn(Opcodes.ALOAD_0);
						cw.ldc(new CstClass(typeName));
						cw.invokeS("roj/plugin/di/DIContext", "_UCB", "(Ljava/lang/Object;Lroj/plugin/di/DIContext;Ljava/lang/Class;)V");
					}
				}
			});
			return true;
		};

		groupBy.keySet().forEach(className -> classLoader.getHooks().declaredClass(className, classNodeHook));
	}
}
