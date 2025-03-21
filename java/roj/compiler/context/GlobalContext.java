package roj.compiler.context;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import roj.asm.*;
import roj.asm.attr.InnerClasses;
import roj.asm.attr.ModuleAttribute;
import roj.asm.type.IType;
import roj.asmx.AnnotationSelf;
import roj.collect.*;
import roj.compiler.LavaFeatures;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.diagnostic.Diagnostic;
import roj.compiler.diagnostic.Kind;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.doc.Javadoc;
import roj.compiler.doc.JavadocProcessor;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;
import roj.text.Interner;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 提供class信息的上下文、编译环境 每个编译器一个，或者说它就是编译器
 * 1. 提供类全限定名(String)到类ASM数据(ConstantData)的映射
 * 2. 一些和类绑定的数据的缓存，它们包括
 *   MethodList，FieldList，他们只以名字区分，不考虑权限或参数数量，前者是为了更好的错误提示而不是仅仅“找不到”，后者是为了varargs和named type，
 *   父类列表，之所以IntBiMap而不是数组是为了有序的同时，还能O(n)获取最近共同祖先
 *   List<IType> getTypeParamOwner(IClass info, IType superType)
 *   InnerClassFlags 获取内部类的真实权限 （TODO 尝试自动应用到类ASM数据上）
 * 3. 短名称缓存 (通过短名称获取全限定名) TypeResolver importAny需要用到
 * 4. 创建MethodWriter，为了以后切换编译目标，比如到C甚至x86
 * 5. 注解处理
 *
 * @author solo6975
 * @since 2021/7/22 19:05
 */
public class GlobalContext implements LavaFeatures {
	public static File cacheFolder = new File(".lavaCache");

	private static final XHashSet.Shape<String, CompileUnit> COMPILE_UNIT_SHAPE = XHashSet.noCreation(CompileUnit.class, "name");
	private static final XHashSet.Shape<IClass, ResolveHelper> CLASS_EXTRA_INFO_SHAPE = XHashSet.shape(IClass.class, ResolveHelper.class, "owner", "_next", Hasher.identity());

	protected final XHashSet<String, CompileUnit> ctx = COMPILE_UNIT_SHAPE.create();

	protected final MyHashMap<String, Object> libraryCache = new MyHashMap<>();
	protected final List<Library> libraries = new SimpleList<>();
	private final List<Library> unenumerableLibraries = new SimpleList<>();

	protected final XHashSet<IClass, ResolveHelper> extraInfos = CLASS_EXTRA_INFO_SHAPE.create();
	protected MyHashMap<String, List<String>> packageFastPath = new MyHashMap<>();
	protected final SimpleList<CompileUnit> generatedCUs = new SimpleList<>();

	protected final LibraryGenerated generated = new LibraryGenerated();
	protected static final class LibraryGenerated implements Library {
		final MyHashMap<String, ClassNode> classes = new MyHashMap<>();
		ModuleAttribute module;

		@Override public ClassNode get(CharSequence name) {return classes.get(name);}
		@Override public String moduleName() {return module == null ? null : module.self.name;}
	}

	{addRuntime();}
	private boolean hasRuntimeLibrary;
	protected void addRuntime() {unenumerableLibraries.addAll(LibrarySymbolCache.MODULES);hasRuntimeLibrary = true;}
	public void addLibrary(Library library) {
		var content = library.content();
		if (content.isEmpty()) {
			unenumerableLibraries.add(library);
		} else {
			for (String className : content)
				libraryCache.put(className, library);
		}
		libraries.add(library);
	}

	public final ClassNode getClassInfo(CharSequence name) {
		ClassNode clz = ctx.get(name);
		if (clz == null) {
			var entry = libraryCache.getEntry(name);
			if (entry != null) {
				synchronized (entry) {
					var value = entry.getValue();
					if (value instanceof ClassNode c) return c;

					clz = ((Library) value).get(name);
					clz = onClassLoaded(entry.k, clz);
					entry.setValue(clz);
				}
				return clz;
			}

			for (int i = unenumerableLibraries.size()-1; i >= 0; i--) {
				var library = unenumerableLibraries.get(i);
				if ((clz = library.get(name)) != null) {
					var key = name.toString();
					clz = onClassLoaded(key, clz);
					synchronized (libraryCache) {libraryCache.put(key, clz);}
					return clz;
				}
			}
		}
		return clz;
	}
	protected ClassNode onClassLoaded(String name, ClassNode clazz) {
		//Library.removeUnrelatedAttribute(clazz);
		return clazz;
	}

	public synchronized void addCompileUnit(CompileUnit unit, boolean generated) {
		CompileUnit prev = ctx.putIfAbsent(unit.name(), unit);
		if (prev != null) throw new IllegalStateException("重复的编译单位: "+unit.name());
		if (generated) generatedCUs.add(unit);
	}
	public void addGeneratedCompileUnits(List<CompileUnit> ctxs) {
		ctxs.addAll(generatedCUs);
		generatedCUs.clear();
	}

	public synchronized void addGeneratedClass(ClassNode data) {
		libraryCache.put(data.name(), generated);
		var prev = generated.classes.putIfAbsent(data.name(), data);
		if (prev != null) throw new IllegalStateException("重复的生成类: "+data.name());
	}
	public Collection<ClassNode> getGeneratedClasses() {return generated.classes.values();}

	@NotNull public synchronized final ResolveHelper getResolveHelper(IClass info) { return extraInfos.computeIfAbsent(info); }
	public synchronized void invalidateResolveHelper(IClass file) {extraInfos.removeKey(file);}

	@NotNull public IntBiMap<String> getParentList(IClass info) {return getResolveHelper(info).getHierarchyList(this);}
	@NotNull public ComponentList getMethodList(IClass info, String name) {return getResolveHelper(info).getMethods(this).getOrDefault(name, ComponentList.NOT_FOUND);}
	@NotNull public ComponentList getFieldList(IClass info, String name) {return getResolveHelper(info).getFields(this).getOrDefault(name, ComponentList.NOT_FOUND);}
	@Nullable public List<IType> getTypeParamOwner(IClass info, String superType) throws ClassNotFoundException {return getResolveHelper(info).getTypeParamOwner(this).get(superType);}
	@NotNull public Map<String, InnerClasses.Item> getInnerClassFlags(IClass info) {return getResolveHelper(info).getInnerClasses();}
	public AnnotationSelf getAnnotationDescriptor(IClass info) {return getResolveHelper(info).annotationInfo();}

	/**
	 * 通过短名称获取全限定名（若存在）
	 * @return 包含候选包名的列表 例如 [java/lang]
	 */
	public List<String> getAvailablePackages(String shortName) {
		if (packageFastPath.isEmpty()) buildPackageCache();
		return packageFastPath.getOrDefault(shortName, Collections.emptyList());
	}
	private synchronized void buildPackageCache() {
		if (!packageFastPath.isEmpty()) return;

		// TODO byModule
		for (Library library : libraries) {
			for (String name : library.getPackageCache()) {
				if (!name.contains("$") && !name.startsWith("[")) addFastPath(name);
			}
		}
		if (hasRuntimeLibrary) {
			for (Library module : unenumerableLibraries) {
				for (String name : module.getPackageCache()) {
					addFastPath(name);
				}
			}
		}

		for (Map.Entry<String, List<String>> entry : packageFastPath.entrySet()) {
			var value = entry.getValue();
			if (value.size() == 1) entry.setValue(Collections.singletonList(Interner.intern(value.get(0))));
			else for (int i = 0; i < value.size(); i++) value.set(i, Interner.intern(value.get(i)));
		}
	}
	private void addFastPath(String n) {
		int i = n.lastIndexOf('/');
		String packag = i < 0 ? "" : n.substring(0, i);
		String name = n.substring(i+1);

		var allPackages = packageFastPath.computeIfAbsent(name, Helpers.fnArrayList());
		if (!allPackages.contains(packag)) allPackages.add(packag);
	}

	public void reset() {
		for (CompileUnit unit : ctx) extraInfos.removeKey(unit);
		ctx.clear();

		for (String s : generated.classes.keySet()) libraryCache.remove(s, generated);
		generated.classes.clear();
		generated.module = null;
	}

	public LocalContext createLocalContext() {return new LocalContext(this);}
	public JavadocProcessor createJavadocProcessor(Javadoc javadoc, CompileUnit file) {return JavadocProcessor.NULL;}

	public void setModule(ModuleAttribute module) {
		if (generated.module != null) report(null, Kind.ERROR, -1, "module.dup");
		generated.module = module;
	}

	// example
	public void runAnnotationProcessor(CompileUnit file, Attributed node, List<AnnotationPrimer> annotations) {}

	public boolean hasFeature(int specId) {return true;}
	@Range(from = 6, to = 21)
	public int getMaximumBinaryCompatibility() {return LavaFeatures.JAVA_17;}

	public Function<Diagnostic, Boolean> reporter = new TextDiagnosticReporter(1, 1, 1);
	public boolean hasError;

	public boolean hasError() {return hasError;}
	public void report(IClass source, Kind kind, int pos, String code) { report(source, kind, pos, code, (Object[]) null);}
	public void report(IClass source, Kind kind, int pos, String code, Object... args) {
		Diagnostic diagnostic = new Diagnostic(source, kind, pos, pos, code, args);
		if (reporter.apply(diagnostic)) hasError = true;
	}
	public void report(IClass source, Kind kind, int start, int end, String code, Object... args) {
		Diagnostic diagnostic = new Diagnostic(source, kind, start, end, code, args);
		if (reporter.apply(diagnostic)) hasError = true;
	}

	private static final ClassNode AnyArray;
	static {
		var array = Object[].class;
		var ref = new ClassNode();
		ref.name("[Ljava/lang/Object;");
		ref.parent(array.getSuperclass().getName().replace('.', '/'));
		for (Class<?> itf : array.getInterfaces()) ref.addInterface(itf.getName().replace('.', '/'));
		ref.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL, "length", "I"));
		ref.methods.add(new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL, "java/lang/Object", "clone", "()Ljava/lang/Object;"));
		AnyArray = ref;
	}
	public ClassNode getArrayInfo(IType type) {return AnyArray;}
	public static List<String> arrayInterfaces() {return AnyArray.interfaces();}
	public static FieldNode arrayLength() {return AnyArray.fields.get(0);}
	public static MethodNode arrayClone() {return AnyArray.methods.get(0);}

	private static final Logger logger = Logger.getLogger("Lavac/Debug");
	public static Logger debugLogger() {return logger;}
}