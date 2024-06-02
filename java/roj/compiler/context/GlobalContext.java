package roj.compiler.context;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.tree.*;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.IType;
import roj.asmx.AnnotationSelf;
import roj.collect.Hasher;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.XHashSet;
import roj.compiler.CompilerSpec;
import roj.compiler.api.impl.ResolveApiImpl;
import roj.compiler.api_rt.*;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.ParamAnnotationRef;
import roj.compiler.asm.Variable;
import roj.compiler.diagnostic.Diagnostic;
import roj.compiler.diagnostic.Kind;
import roj.compiler.diagnostic.SimpleDiagnosticListener;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.ResolveHelper;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.*;
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
 * 5. 注解处理（这块待重构）
 *
 * @author solo6975
 * @since 2021/7/22 19:05
 */
public class GlobalContext implements CompilerSpec, LavaApi {
	private static final XHashSet.Shape<String, CompileUnit> COMPILE_UNIT_SHAPE = XHashSet.noCreation(CompileUnit.class, "name", "_next", Hasher.defaul());
	private static final XHashSet.Shape<IClass, ResolveHelper> CLASS_EXTRA_INFO_SHAPE = XHashSet.shape(IClass.class, ResolveHelper.class, "owner", "_next", Hasher.identity());

	private final XHashSet<String, CompileUnit> ctx = COMPILE_UNIT_SHAPE.create();
	private final MyHashMap<String, Library> libraries = new MyHashMap<>();
	private final XHashSet<IClass, ResolveHelper> extraInfos = CLASS_EXTRA_INFO_SHAPE.create();
	private final MyHashMap<String, List<String>> packageFastPath = new MyHashMap<>();

	private final LibraryGenerated generated = new LibraryGenerated();
	private static final class LibraryGenerated implements Library {
		final MyHashMap<String, ConstantData> classes = new MyHashMap<>();
		@Override
		public Set<String> content() {return Collections.emptySet();}
		@Override
		public IClass get(CharSequence name) {return classes.get(name);}
	}

	public void addLibrary(Library library) {
		for (String className : library.content())
			libraries.put(className, library);
	}

	// ◢◤
	public IClass getClassInfo(CharSequence name) {
		IClass clz = ctx.get(name);
		if (clz == null) {
			clz = libraries.getOrDefault(name, LibraryRuntime.INSTANCE).get(name);
			if (clz == null && resolveApi != null) {
				clz = resolveApi.preFilter(name);
				if (clz == null) return null;

				synchronized (libraries) {
					libraries.put(name.toString(), resolveApi);
				}
			}
		}

		if (resolveApi != null) resolveApi.postFilter(clz);
		return clz;
	}

	public void addCompileUnit(CompileUnit unit) {
		CompileUnit prev = ctx.put(unit.name, unit);
		if (prev != null) throw new IllegalStateException("重复的编译单位: " + unit.name);
	}

	public synchronized void addGeneratedClass(ConstantData data) {
		libraries.put(data.name, generated);
		var prev = generated.classes.put(data.name, data);
		if (prev != null) throw new IllegalStateException("重复的生成类: " + data.name);
	}
	public Collection<ConstantData> getGeneratedClasses() {return generated.classes.values();}

	public void reset() {
		for (CompileUnit unit : ctx) extraInfos.removeKey(unit);
		ctx.clear();

		for (String s : generated.classes.keySet()) libraries.remove(s, generated);
		generated.classes.clear();
	}

	public final ResolveHelper getResolveHelper(IClass info) { return extraInfos.computeIfAbsent(info); }
	public IntBiMap<String> parentList(IClass info) throws ClassNotFoundException { return getResolveHelper(info).getClassList(this); }
	public ComponentList methodList(IClass info, String name) throws TypeNotPresentException { return getResolveHelper(info).findMethod(this, name); }
	public ComponentList fieldList(IClass info, String name) throws TypeNotPresentException { return getResolveHelper(info).findField(this, name); }
	@NotNull
	public List<IType> getTypeParamOwner(IClass info, IType superType) throws ClassNotFoundException {
		Map<String, List<IType>> map = getResolveHelper(info).getTypeParamOwner(this);

		List<IType> tpws = map.get(superType.owner());
		if (tpws != null) return tpws;
		throw new IllegalArgumentException(superType+"不是"+info.name()+"的泛型超类或超接口");
	}
	public MyHashMap<String, InnerClasses.InnerClass> getInnerClassFlags(IClass info) {return getResolveHelper(info).getInnerClassFlags(this);}
	public AnnotationSelf getAnnotationDescriptor(IClass clz) {return getResolveHelper(clz).annotationInfo();}

	/**
	 * 通过短名称获取全限定名（若存在）
	 * @return 包含候选包名的列表 例如 [java/lang]
	 */
	public List<String> getAvailablePackages(String shortName) {
		if (packageFastPath.isEmpty()) {
			synchronized (this) {
				if (packageFastPath.isEmpty()) {
					for (Map.Entry<String, Library> entry : libraries.entrySet()) {
						addFastPath(entry.getKey());
					}
					for (String n : LibraryRuntime.INSTANCE.content()) {
						addFastPath(n);
					}
					for (Map.Entry<String, List<String>> entry : packageFastPath.entrySet()) {
						List<String> value = entry.getValue();
						if (value.size() == 1) entry.setValue(Collections.singletonList(value.get(0)));
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

	public boolean applicableToNode(AnnotationSelf ctx, Attributed key) {
		int flag;
		if (key instanceof FieldNode) {
			flag = AnnotationSelf.FIELD;
		} else if (key instanceof MethodNode m) {
			flag = m.name().startsWith("<") ? AnnotationSelf.CONSTRUCTOR : AnnotationSelf.METHOD;
		} else if (key instanceof IClass c) {
			// TODO package ??
			flag = (c.modifier()&Opcodes.ACC_ANNOTATION) != 0 ? AnnotationSelf.ANNOTATION_TYPE : AnnotationSelf.TYPE;
		} else if (key instanceof IType) {
			// TYPE_PARAMETER, TYPE_USE
			throw new ResolveException("unsupported case");
		} else if (key instanceof ParamAnnotationRef) {
			flag = AnnotationSelf.PARAMETER;
		} else if (key instanceof Variable) {
			flag = AnnotationSelf.LOCAL_VARIABLE;
		} else {
			throw new ResolveException("unsupported case");
		}
		return (ctx.applicableTo&flag) != 0;
	}

	public boolean hasError() {return hasError;}

	public MethodWriter createMethodPoet(ConstantData unit, MethodNode node) {
		return new MethodWriter(unit, node);
	}

	public void invokeAnnotationProcessor(CompileUnit unit, Attributed key, List<? extends Annotation> annotations) {
		// DEFINE BY USER
		debugLogger().info("invokeAnnotationProcessor(): unit = " + unit + ", key = " + key + ", annotations = " + annotations);
	}

	public boolean isSpecEnabled(int specId) {return specId != DISABLE_RAW_TYPE;}

	public Consumer<Diagnostic> listener = new SimpleDiagnosticListener(1, 1, 1);
	public boolean hasError;

	@Override
	public void report(IClass source, Kind kind, int pos, String code) { report(source, kind, pos, code, (Object[]) null);}
	@Override
	public void report(IClass source, Kind kind, int pos, String code, Object... args) {
		Diagnostic diagnostic = new Diagnostic(source, kind, pos, pos, code, args);
		if (kind.ordinal() >= Kind.ERROR.ordinal()) hasError = true;
		listener.accept(diagnostic);
	}

	private static ConstantData AnyArray;
	public static IClass anyArray() {
		if (AnyArray == null) {
			Class<Object[]> array = Object[].class;
			ConstantData data = new ConstantData();
			data.name("java/lang/Array");
			data.parent(array.getSuperclass().getName().replace('.', '/'));
			for (Class<?> itf : array.getInterfaces())
				data.addInterface(itf.getName().replace('.', '/'));
			data.newField(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL, "length", "I");
			AnyArray = data;
		}
		return AnyArray;
	}
	public static FieldNode arrayLength() {return (FieldNode) anyArray().fields().get(0);}


	private static final Logger logger = Logger.getLogger("Lavac/Debug");
	public static Logger debugLogger() {return logger;}

	@Override
	public AnnotationApi getAnnotationApi() {
		throw new UnsupportedOperationException("not implemented yet");
	}

	@Override
	public ExprApi getExprApi() {
		return null;
	}

	@Override
	public LexApi getLexApi() {
		return null;
	}

	private ResolveApiImpl resolveApi;
	@Override
	public void addResolveListener(int priority, DynamicResolver dtr) {
		if (resolveApi == null) resolveApi = new ResolveApiImpl();
		resolveApi.addTypeResolver(priority, dtr);
	}
}