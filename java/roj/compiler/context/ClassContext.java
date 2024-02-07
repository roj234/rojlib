package roj.compiler.context;

import roj.asm.Opcodes;
import roj.asm.tree.Attributed;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.IType;
import roj.asmx.AnnotationSelf;
import roj.collect.*;
import roj.compiler.CompilerConfig;
import roj.compiler.JavaLexer;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Diagnostic;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;
import roj.config.ParseException;
import roj.text.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author solo6975
 * @since 2021/7/22 19:05
 */
public class ClassContext implements CompilerConfig {
	private static final XHashSet.Shape<String, CompileUnit> COMPILE_UNIT_SHAPE = XHashSet.noCreation(CompileUnit.class, "name", "_next", Hasher.defaul());
	private static final XHashSet.Shape<IClass, ResolveHelper> CLASS_EXTRA_INFO_SHAPE = XHashSet.shape(IClass.class, ResolveHelper.class, "owner", "_next", Hasher.identity());

	private final XHashSet<String, CompileUnit> ctx = COMPILE_UNIT_SHAPE.create();
	private final MyHashMap<String, Library> libraries = new MyHashMap<>();
	private final XHashSet<IClass, ResolveHelper> extraInfos = CLASS_EXTRA_INFO_SHAPE.create();

	@Deprecated
	public static AnnotationSelf getAnnotationInfo(List<? extends Annotation> list) {
		throw new NoSuchMethodError();
	}

	public boolean isSpecEnabled(int specId) {
		return true;
	}

	public void addLibrary(Library library) {
		for (String className : Objects.requireNonNull(library.content()))
			libraries.put(className, library);
	}

	// ◢◤
	public IClass getClassInfo(CharSequence name) {
		IClass clz = ctx.get(name);
		if (clz != null) return clz;

		Library l = libraries.get(name);
		if (l != null) return l.get(name);

		clz = LibraryRuntime.INSTANCE.get(name);
		if (clz != null) {
			synchronized (libraries) {
				libraries.put(name.toString(), LibraryRuntime.INSTANCE);
			}
			return clz;
		}

		return null;
	}

	public final ResolveHelper getResolveHelper(IClass info) { return extraInfos.computeIfAbsent(info); }
	public IntBiMap<String> parentList(IClass info) throws ClassNotFoundException { return getResolveHelper(info).getClassList(this); }
	public ComponentList methodList(IClass info, String name) throws ClassNotFoundException { return getResolveHelper(info).findMethod(this, name); }
	public ComponentList fieldList(IClass info, String name) throws ClassNotFoundException { return getResolveHelper(info).findField(this, name); }
	public List<IType> getTypeParamOwner(IClass info, IType superType) throws ClassNotFoundException {
		Map<String, List<IType>> map = getResolveHelper(info).getTypeParamOwner(this);

		List<IType> tpws = map.get(superType.owner());
		if (tpws != null) return tpws;
		throw new IllegalArgumentException(superType+"不是"+info.name()+"的泛型超类或超接口");
	}
	public MyHashMap<String, InnerClasses.InnerClass> getInnerClassFlags(IClass info) {
		return getResolveHelper(info).getInnerClassFlags(this);
	}

	public void addCompileUnit(CompileUnit unit) {
		CompileUnit prev = ctx.put(unit.name, unit);
		if (prev != null) throw new IllegalStateException("重复的编译单位: " + unit.name);
	}

	public MethodWriter createMethodPoet(CompileUnit unit, MethodNode node) {
		return new MethodWriter(unit, node);
	}

	public boolean applicableToNode(AnnotationSelf ctx, Attributed key) {
		return false;
	}

	public void invokePartialAnnotationProcessor(CompileUnit unit, AnnotationPrimer annotation, MyHashSet<String> missed) {}

	public void invokeAnnotationProcessor(CompileUnit unit, Attributed key, List<AnnotationPrimer> annotations) {}

	public AnnotationSelf getAnnotationDescriptor(IClass clz) {
		return clz instanceof CompileUnit ? ((CompileUnit) clz).annoInfo : getResolveHelper(clz).annotationInfo();
	}

	public boolean hasAnnotation(IClass clazz, String name) {
		return false;
	}

	public Consumer<Diagnostic> listener;
	public boolean hasError;

	public void report(CompileUnit source, Kind kind, int pos, String o) {
		if (kind == Kind.ERROR) hasError = true;
		new ParseException(source.lex().getText(), kind+":"+JavaLexer.translate.translate(o)+"\nin "+source.getFilePath(), pos).printStackTrace();
		//listener.report();
	}

	public boolean hasError() {
		return hasError;
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


	private static final Logger logger = Logger.getLogger("Lavac/Debug");
	public static Logger debugLogger() {
		return logger;
	}
}