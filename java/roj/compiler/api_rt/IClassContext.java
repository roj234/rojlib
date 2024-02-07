package roj.compiler.api_rt;

import roj.asm.tree.IClass;
import roj.collect.IntBiMap;
import roj.compiler.context.CompileUnit;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;

/**
 * @author Roj234
 * @since 2024/2/20 0020 17:22
 */
public interface IClassContext {
	boolean isSpecEnabled(int specId);

	IClass getClassInfo(CharSequence name);

	IntBiMap<String> parentList(IClass info) throws ClassNotFoundException;
	ComponentList methodList(IClass info, String name) throws ClassNotFoundException;
	ComponentList fieldList(IClass info, String name) throws ClassNotFoundException;

	boolean hasError();
	void report(CompileUnit source, Kind kind, int pos, String o);
}