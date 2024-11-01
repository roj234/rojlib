package roj.compiler.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.tree.IClass;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.IType;
import roj.collect.IntBiMap;
import roj.compiler.context.Library;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;
import roj.util.TypedKey;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/2/20 0020 16:57
 */
public interface LavaApi {
	boolean hasFeature(int specId);

	IClass getClassInfo(CharSequence name);
	void addLibrary(Library library);

	@NotNull ResolveHelper getResolveHelper(@NotNull IClass info);
	void invalidateResolveHelper(IClass info);

	@NotNull IntBiMap<String> getParentList(IClass info) throws TypeNotPresentException;
	@Nullable ComponentList getMethodList(IClass info, String name) throws TypeNotPresentException;
	@Nullable ComponentList getFieldList(IClass info, String name) throws TypeNotPresentException;
	@Nullable List<IType> getTypeParamOwner(IClass info, String superType) throws ClassNotFoundException;
	@Nullable Map<String, InnerClasses.Item> getInnerClassFlags(IClass info);

	/**
	 * 通过短名称获取全限定名（若存在）
	 * @return 包含候选包名的列表 例如 [java/lang]
	 */
	List<String> getAvailablePackages(String shortName);

	void report(IClass source, Kind kind, int pos, String code);
	void report(IClass source, Kind kind, int pos, String code, Object... args);

	<T> T attachment(TypedKey<T> key);
	<T> T attachment(TypedKey<T> key, T val);

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
	 * Param 包括下列类别 - METHOD_PARAMETER (ParamAnnotationRef, NOT_IMPLEMENTED[Classpath])
	 * TypeUse
	 */
	void addAnnotationProcessor(Processor processor);
	// endregion
}