package roj.compiler.api_rt;

import org.jetbrains.annotations.NotNull;
import roj.asm.tree.IClass;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.IType;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/2/20 0020 16:57
 */
public interface LavaApi {
	public boolean isSpecEnabled(int specId);

	public IClass getClassInfo(CharSequence name);

	public ResolveHelper getResolveHelper(IClass info);
	public IntBiMap<String> parentList(IClass info) throws ClassNotFoundException;
	public ComponentList methodList(IClass info, String name) throws TypeNotPresentException;
	public ComponentList fieldList(IClass info, String name) throws TypeNotPresentException;
	@NotNull
	public List<IType> getTypeParamOwner(IClass info, IType superType) throws ClassNotFoundException;
	public MyHashMap<String, InnerClasses.InnerClass> getInnerClassFlags(IClass info);

	/**
	 * 通过短名称获取全限定名（若存在）
	 * @return 包含候选包名的列表 例如 [java/lang]
	 */
	public List<String> getAvailablePackages(String shortName);

	public void report(IClass source, Kind kind, int pos, String code);
	public void report(IClass source, Kind kind, int pos, String code, Object... args);

	AnnotationApi getAnnotationApi();
	ExprApi getExprApi();
	LexApi getLexApi();

	void addResolveListener(int priority, DynamicResolver dtr);
}