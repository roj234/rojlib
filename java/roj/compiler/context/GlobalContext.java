package roj.compiler.context;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrModule;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asmx.AnnotationSelf;
import roj.collect.*;
import roj.compiler.CompilerSpec;
import roj.compiler.JavaLexer;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.BlockParser;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.diagnostic.Diagnostic;
import roj.compiler.diagnostic.Kind;
import roj.compiler.diagnostic.SimpleDiagnosticListener;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.auto.Serializer;
import roj.config.auto.Serializers;
import roj.crypt.CRC32s;
import roj.io.IOUtil;
import roj.text.Interner;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
public class GlobalContext implements CompilerSpec {
	private static final XHashSet.Shape<String, CompileUnit> COMPILE_UNIT_SHAPE = XHashSet.noCreation(CompileUnit.class, "name", "_next", Hasher.defaul());
	private static final XHashSet.Shape<IClass, ResolveHelper> CLASS_EXTRA_INFO_SHAPE = XHashSet.shape(IClass.class, ResolveHelper.class, "owner", "_next", Hasher.identity());

	protected final XHashSet<String, CompileUnit> ctx = COMPILE_UNIT_SHAPE.create();
	protected final MyHashMap<String, Library> libraries = new MyHashMap<>();
	protected final List<Library> fallbackLibraries = new SimpleList<>();
	protected final XHashSet<IClass, ResolveHelper> extraInfos = CLASS_EXTRA_INFO_SHAPE.create();
	protected MyHashMap<String, List<String>> packageFastPath = new MyHashMap<>();
	protected final SimpleList<CompileUnit> generatedCUs = new SimpleList<>();

	protected final LibraryGenerated generated = new LibraryGenerated();
	protected static final class LibraryGenerated implements Library {
		final MyHashMap<String, ConstantData> classes = new MyHashMap<>();
		AttrModule module;

		@Override
		public ConstantData get(CharSequence name) {return classes.get(name);}
		@Override
		public String getModule(String className) {return module == null ? null : module.self.name;}
	}

	public void addLibrary(Library library) {
		var set = library.content();
		if (set.isEmpty()) {
			fallbackLibraries.add(library);
		} else {
			for (String className : set)
				libraries.put(className, library);
		}
	}

	public ConstantData getClassInfo(CharSequence name) {
		ConstantData clz = ctx.get(name);
		if (clz == null) {
			var library = libraries.get(name);
			if (library != null) return library.get(name);
			for (int i = 0; i < fallbackLibraries.size(); i++) {
				library = fallbackLibraries.get(i);
				if ((clz = library.get(name)) != null) return clz;
			}
			clz = LibraryRuntime.INSTANCE.get(name);
		}
		return clz;
	}

	public void addCompileUnit(CompileUnit unit, boolean generated) {
		CompileUnit prev = ctx.put(unit.name, unit);
		if (prev != null) throw new IllegalStateException("重复的编译单位: "+unit.name);
		if (generated) generatedCUs.add(unit);
	}
	public void addGeneratedCompileUnits(List<CompileUnit> ctxs) {
		ctxs.addAll(generatedCUs);
		generatedCUs.clear();
	}

	public synchronized void addGeneratedClass(ConstantData data) {
		libraries.put(data.name, generated);
		var prev = generated.classes.put(data.name, data);
		if (prev != null) throw new IllegalStateException("重复的生成类: " + data.name);
	}
	public Collection<ConstantData> getGeneratedClasses() {return generated.classes.values();}

	@NotNull
	public final ResolveHelper getResolveHelper(IClass info) { return extraInfos.computeIfAbsent(info); }
	@NotNull
	public IntBiMap<String> getParentList(IClass info) throws ClassNotFoundException {return getResolveHelper(info).getClassList(this);}
	@Nullable
	public ComponentList getMethodList(IClass info, String name) throws TypeNotPresentException {return getResolveHelper(info).findMethod(this, name);}
	@Nullable
	public ComponentList getFieldList(IClass info, String name) throws TypeNotPresentException {return getResolveHelper(info).findField(this, name);}
	@Nullable
	public List<IType> getTypeParamOwner(IClass info, String superType) throws ClassNotFoundException {return getResolveHelper(info).getTypeParamOwner(this).get(superType);}
	public MyHashMap<String, InnerClasses.Item> getInnerClassFlags(IClass info) {return getResolveHelper(info).getInnerClasses();}
	public AnnotationSelf getAnnotationDescriptor(IClass clz) {return getResolveHelper(clz).annotationInfo();}

	public File packageCacheFile;
	public int getLibraryHash() {
		ByteList list = new ByteList();
		for (Library library : new MyHashSet<>(libraries.values()))
			list.putInt(library.fileHashCode());
		int hash = CRC32s.once(list.list, 0, list.wIndex());
		list._free();
		return hash;
	}
	/**
	 * 通过短名称获取全限定名（若存在）
	 * @return 包含候选包名的列表 例如 [java/lang]
	 */
	@SuppressWarnings("unchecked")
	public List<String> getAvailablePackages(String shortName) {
		if (packageFastPath.isEmpty()) {
			long time = System.nanoTime();
			block:
			synchronized (this) {
				if (packageFastPath.isEmpty()) {
					var serializer = (Serializer<MyHashMap<String, List<String>>>) Serializers.SAFE.serializer(Signature.parseGeneric("Lroj/collect/MyHashMap<Ljava/lang/String;Ljava/util/List<Ljava/lang/String;>;>;"));

					File cache = packageCacheFile;
					if (cache != null && cache.isFile()) {
						try {
							packageFastPath = ConfigMaster.XNBT.readObject(serializer, cache);
							System.out.println("Read PackageCache: "+(System.nanoTime() - time)/1000000d+"ms");
							break block;
						} catch (IOException | ParseException e) {
							debugLogger().warn("包缓存读取失败", e);
						}
					}

					for (String entry : libraries.keySet()) {
						if (!entry.contains("$"))
							addFastPath(entry);
					}

					// how to deal with rt.jar ?
					for (String n : LibraryRuntime.INSTANCE.content()) {
						if (!n.contains("$") &&
							(n.startsWith("java/") || n.startsWith("javax/") ||
							LibraryRuntime.INSTANCE.getModule(n).startsWith("jdk.unsupported"))) {

							try {
								var in = LibraryRuntime.INSTANCE.getResource(n+".class");
								var acc = Parser.parseAccess(IOUtil.getSharedByteBuf().readStreamFully(in), false);
								if ((acc.acc&Opcodes.ACC_PUBLIC) == 0) continue;
							} catch (Exception e) {
								e.printStackTrace();
							}

							addFastPath(n);
						}
					}

					for (Map.Entry<String, List<String>> entry : packageFastPath.entrySet()) {
						var value = entry.getValue();
						if (value.size() == 1) entry.setValue(Collections.singletonList(Interner.intern(value.get(0))));
						else for (int i = 0; i < value.size(); i++) value.set(i, Interner.intern(value.get(i).intern()));
					}

					System.out.println("Build PackageCache: "+(System.nanoTime() - time)/1000000d+"ms");

					if (cache != null) try {
						ConfigMaster.XNBT.writeObject(serializer, packageFastPath, cache);
					} catch (IOException e) {
						debugLogger().warn("包缓存保存失败", e);
					}
				}
			}
		}
		return packageFastPath.getOrDefault(shortName, Collections.emptyList());
	}
	private void addFastPath(String n) {
		int i = n.lastIndexOf('/');
		String packag = i < 0 ? "" : n.substring(0, i);
		String name = n.substring(i+1);

		packageFastPath.computeIfAbsent(name, Helpers.fnArrayList()).add(packag);
	}

	public void invalidateResolveHelper(IClass file) {
		extraInfos.removeKey(file);
	}

	public void reset() {
		for (CompileUnit unit : ctx) extraInfos.removeKey(unit);
		ctx.clear();

		for (String s : generated.classes.keySet()) libraries.remove(s, generated);
		generated.classes.clear();
	}

	public JavaLexer createLexer() {return new JavaLexer();}
	public LocalContext createLocalContext() {return new LocalContext(this);}

	public ExprParser createExprParser(LocalContext ctx) {return new ExprParser(ctx);}
	public BlockParser createBlockParser(LocalContext ctx) {return new BlockParser(ctx);}
	public MethodWriter createMethodPoet(ConstantData file, MethodNode node) {return new MethodWriter(file, node);}

	public void setModule(AttrModule module) {
		if (generated.module != null) report(null, Kind.ERROR, -1, "module.dup");
		generated.module = module;
	}

	// DEFINE BY USER
	public void invokeAnnotationProcessor(CompileUnit file, Attributed node, List<AnnotationPrimer> annotations) {
		for (var annotation : annotations) {
			if (annotation.type().equals("java/lang/Override") && annotation.values != Collections.EMPTY_MAP) {
				report(file, Kind.ERROR, annotation.pos, "annotation.override");
			}
			if (annotation.type().equals("roj/compiler/api/Ignore")) {
				debugLogger().info("\n\n当前的错误已被忽略\n\n");
				// 临时解决方案
				hasError = false;
			}
		}
	}

	public boolean isSpecEnabled(int specId) {return true;}

	public Consumer<Diagnostic> listener = new SimpleDiagnosticListener(1, 1, 1);
	public boolean hasError;

	public boolean hasError() {return hasError;}
	public void report(IClass source, Kind kind, int pos, String code) { report(source, kind, pos, code, (Object[]) null);}
	public void report(IClass source, Kind kind, int pos, String code, Object... args) {
		Diagnostic diagnostic = new Diagnostic(source, kind, pos, pos, code, args);
		if (kind.ordinal() >= Kind.ERROR.ordinal()) hasError = true;
		listener.accept(diagnostic);
	}
	public void report(IClass source, Kind kind, int start, int end, String code, Object... args) {
		Diagnostic diagnostic = new Diagnostic(source, kind, start, end, code, args);
		if (kind.ordinal() >= Kind.ERROR.ordinal()) hasError = true;
		listener.accept(diagnostic);
	}

	private static final ConstantData AnyArray;
	static {
		var array = Object[].class;
		var ref = new ConstantData();
		ref.name("java/lang/Array");
		ref.parent(array.getSuperclass().getName().replace('.', '/'));
		for (Class<?> itf : array.getInterfaces()) ref.addInterface(itf.getName().replace('.', '/'));
		ref.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL, "length", "I"));
		ref.methods.add(new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL, "java/lang/Object", "clone", "()Ljava/lang/Object;"));
		AnyArray = ref;
	}
	public static ConstantData anyArray() {return AnyArray;}
	public static FieldNode arrayLength() {return AnyArray.fields.get(0);}
	public static MethodNode arrayClone() {return AnyArray.methods.get(0);}

	private static final Logger logger = Logger.getLogger("Lavac/Debug");
	public static Logger debugLogger() {return logger;}
}