package roj.lavac.parser;

import roj.asm.tree.AnnotationClass;
import roj.asm.tree.Attributed;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.util.AttrHelper;
import roj.asm.util.ClassUtil;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.ParseException;
import roj.lavac.CompilerConfig;
import roj.lavac.asm.AnnotationPrimer;
import roj.lavac.util.Library;
import roj.lavac.util.LibraryRuntime;
import roj.util.Helpers;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import java.util.List;
import java.util.Map;

/**
 * @author solo6975
 * @since 2021/7/22 19:05
 */
public class CompileContext implements CompilerConfig {
	private final MyHashMap<String, CompileUnit> ctx = new MyHashMap<>();
	private final MyHashMap<String, Library> libraries = new MyHashMap<>();

	public boolean isSpecEnabled(int specId) {
		return true;
	}

	public void config(Map<String, String> config) throws ParseException {}

	public void addLibrary(Library library) {
		for (String className : library.content()) {
			libraries.put(className, library);
		}
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

	public void addCompileUnit(CompileUnit unit) {
		CompileUnit prev = ctx.put(unit.name, unit);
		if (prev != null) throw new IllegalStateException("重复的编译单位: " + unit.name);
	}

	public boolean canInstanceOf(String toTest, String inst, int itfFlag) {
		return ClassUtil.getInstance().instanceOf(Helpers.cast(ctx), toTest, inst, itfFlag);
	}

	public MethodWriterL createMethodPoet(CompileUnit unit, MethodNode node) {
		return new MethodWriterL(node);
	}

	public String findSuitableMethod(MethodWriterL stack, IClass type, String name, Object context) throws ParseException {
		return null;
	}

	public boolean applicableToNode(AnnotationClass ctx, Attributed key) {
		return false;
	}

	public void invokePartialAnnotationProcessor(CompileUnit unit, AnnotationPrimer annotation, MyHashSet<String> missed) {}

	public void invokeAnnotationProcessor(CompileUnit unit, Attributed key, List<AnnotationPrimer> annotations) {}

	public static AnnotationClass getAnnotationDescriptor(IClass clz) {
		return clz instanceof CompileUnit ? ((CompileUnit) clz).annoInfo : AttrHelper.getAnnotationInfo(clz);
	}

	public DiagnosticListener<CompileUnit> listener;
	public boolean hasError;

	public void report(CompileUnit source, Diagnostic.Kind kind, int pos, String o) {
		hasError = true;
		System.out.println(kind+" report in " + source.getFilePath());
		System.out.println("offset " + pos + " desc:"+o);
		new Throwable().printStackTrace();
		//listener.report();
	}

	public boolean hasError() {
		return hasError;
	}
}