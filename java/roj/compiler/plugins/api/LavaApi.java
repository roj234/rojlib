package roj.compiler.plugins.api;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.IType;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.compiler.asm.Variable;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/2/20 0020 16:57
 */
public interface LavaApi {
	boolean isSpecEnabled(int specId);

	IClass getClassInfo(CharSequence name);

	ResolveHelper getResolveHelper(IClass info);
	IntBiMap<String> getParentList(IClass info) throws ClassNotFoundException;
	ComponentList getMethodList(IClass info, String name) throws TypeNotPresentException;
	ComponentList getFieldList(IClass info, String name) throws TypeNotPresentException;
	@Nullable
	List<IType> getTypeParamOwner(IClass info, String superType) throws ClassNotFoundException;
	MyHashMap<String, InnerClasses.Item> getInnerClassFlags(IClass info);

	/**
	 * 通过短名称获取全限定名（若存在）
	 * @return 包含候选包名的列表 例如 [java/lang]
	 */
	List<String> getAvailablePackages(String shortName);

	void report(IClass source, Kind kind, int pos, String code);
	void report(IClass source, Kind kind, int pos, String code, Object... args);

	ExprApi getExprApi();

	// region JavaLexer
	int tokenCreate(String token, boolean literalToken, int tokenCategory);
	void tokenRename(int token, String name);
	void tokenAlias(int token, String alias);
	void tokenAliasLiteral(String alias, String literal);
	// endregion
	// region 类的解析 | Class resolver
	void addResolveListener(int priority, Resolver resolver);
	// endregion
	// region 注解处理器 | Annotation processor
	/**
	 * 处理 Class Field Method Parameter
	 * Class 包括下列类别 - TYPE ANNOTATION_TYPE PACKAGE MODULE (CompileUnit, AccessData[Classpath])
	 * Field 包括下列类别 - FIELD (FieldNode, AccessNode[Classpath])
	 * Method包括下列类别 - METHOD (MethodNode, AccessNode[Classpath])
	 * Param 包括下列类别 - METHOD_PARAMETER (ParamAnnotationRef, NOT_SUPPORTED[Classpath])
	 */
	void addGenericProcessor(Processor processor);
	void addGenericProcessor(Processor processor, int stage, int prefilter);

	/**
	 * TYPE_USE  (未实现)
	 * \@XXX Type(var/const) itr;
	 */
	void addLocalVariableAnnotationProcessor(String annotation, Stage4Processor<Variable> processor);

	interface Stage4Processor<SUBJECT> { void accept(IClass file, MethodNode node, Annotation annotation, SUBJECT subject); }
	// endregion
}