package roj.compiler.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.IClass;
import roj.asm.MethodNode;
import roj.asm.attr.InnerClasses;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asmx.AnnotationRepo;
import roj.collect.IntBiMap;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.UnaryPreNode;
import roj.compiler.context.Library;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.stc.StreamChain;
import roj.compiler.plugins.stc.StreamChainExpr;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;
import roj.config.ParseException;
import roj.util.TypedKey;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/2/20 0020 16:57
 */
public interface LavaApi {
	boolean hasFeature(int specId);

	ClassNode getClassInfo(CharSequence name);
	void addLibrary(Library library);

	@NotNull ResolveHelper getResolveHelper(@NotNull IClass info);
	void invalidateResolveHelper(IClass info);

	@NotNull IntBiMap<String> getHierarchyList(IClass info);
	@NotNull ComponentList getMethodList(IClass info, String name);
	@NotNull ComponentList getFieldList(IClass info, String name);
	@Nullable List<IType> getTypeParamOwner(IClass info, String superType) throws ClassNotFoundException;
	@NotNull Map<String, InnerClasses.Item> getInnerClassFlags(IClass info);

	/**
	 * 通过短名称获取全限定名（若存在）
	 * @return 包含候选包名的列表 例如 [java/lang]
	 */
	List<String> getAvailablePackages(String shortName);

	void report(IClass source, Kind kind, int pos, String code);
	void report(IClass source, Kind kind, int pos, String code, Object... args);

	// 沙盒类加载器
	void addSandboxWhitelist(String packageOrTypename, boolean childInheritance);
	void addSandboxBlacklist(String packageOrTypename, boolean childInheritance);
	Object createSandboxInstance(ClassNode data);
	void addSandboxClass(String className, byte[] data);
	Class<?> loadSandboxClass(String className, boolean resolve);

	// 存放对象，什么都行
	<T> T attachment(TypedKey<T> key);
	<T> T attachment(TypedKey<T> key, T val);

	//region 注解处理API
	/**
	 * 处理 Class Field Method Parameter
	 * Class 包括下列类别 - TYPE ANNOTATION_TYPE PACKAGE MODULE (CompileUnit, AccessData[Classpath])
	 * Field 包括下列类别 - FIELD (FieldNode, AccessNode[Classpath])
	 * Method包括下列类别 - METHOD (MethodNode, AccessNode[Classpath])
	 * Param 包括下列类别 - METHOD_PARAMETER (ParamAnnotationRef, NOT_IMPLEMENTED[Classpath])
	 * TypeUse
	 */
	void addAnnotationProcessor(Processor processor);

	/**
	 * 获取classpath中的所有注解，此方法初始化时很慢
	 */
	AnnotationRepo getClasspathAnnotations();
	//endregion
	//region 库加载API
	/**
	 * @since 2024/5/21 2:47
	 */
	@FunctionalInterface
	interface Resolver {
		/**
		 * 触发时机: {@link Library}中的类第一次被加载入{@link roj.compiler.context.GlobalContext}时<br>
		 * 用途: 修改某些类中的方法, 仅在需要用到时, 或者作为类加载限制的兜底措施(虽然我觉得package-restricted足够了)<br>
		 * 如果无论是否加载都要修改，请看{@link roj.compiler.context.GlobalContext#addLibrary(Library)}
		 */
		ClassNode classResolved(ClassNode info);
	}

	void addResolveListener(int priority, Resolver resolver);
	//endregion
	//region 词法分析API
	int tokenCreate(String token, int tokenCategory);
	void tokenAlias(int token, String alias);
	void tokenAliasLiteral(String alias, String literal);
	//endregion
	//region 表达式解析API
	/**
	 * 表达式解析流程:<br>
	 * 一次 不可重复前缀一元运算符 (++ --)<br>
	 * 多次 可重复前缀一元运算符 (+ - ! ~ 类型转换)<br>
	 * 一次 值生成运算符 (new this super 常量 类型[.class .this .super] Lava.EasyMap Lava.NamedParamList 嵌套数组创建省略new switch表达式模式)<br>
	 * 多次 值转换运算符 (方法调用 数组访问 字段读取)<br>
	 * 一次 值终止运算符 (后缀++ -- 赋值 ::)<p>
	 * 或<p>
	 * 一次 表达式生成运算符 (lambda)<br>
	 * <p>
	 * 而后再解析二元运算符<p>
	 * * 代码与此注释未必相同，不过结果相同
	 * <p>
	 * 所有名称为token的参数都是运算符，<br>
	 * 它们可以是任意字符串、可以包含空白字符、并且不能与其它注册的运算符重复
	 * @since 2024/2/20 0020 1:31
	 */
	@FunctionalInterface
	interface StartOp { ExprNode parse(LocalContext ctx) throws ParseException;}

	@FunctionalInterface
	interface ContinueOp<T extends ExprNode> { T parse(LocalContext ctx, T node) throws ParseException;}

	@FunctionalInterface
	interface BinaryOp { ExprNode parse(LocalContext ctx, ExprNode left, ExprNode right);}

	/**
	 * 新增前缀运算符, 后缀可以直接使用TerminalOp
	 */
	void newUnaryOp(String token, ContinueOp<UnaryPreNode> parser);

	/**
	 * 新增表达式生成运算符<br>
	 * 该运算符自身就是一个完整的表达式<br>
	 * 值模式流程：(生成 -> 转换 -> 终止)
	 */
	void newExprOp(String token, StartOp parser);

	/**
	 * 新增值生成运算符
	 * 参考: &lt;minecraft:stone&gt;
	 */
	void newStartOp(String token, StartOp parser);
	/**
	 * 新增值转换运算符
	 */
	void newContinueOp(String token, ContinueOp<ExprNode> parser);
	/**
	 * 新增值终止运算符
	 */
	void newTerminalOp(String token, ContinueOp<ExprNode> parser);
	/**
	 * 新增一个二元运算符
	 * @param token 运算符 如果重复会报错
	 * @param priority 运算符优先级，不加括号时，越大越晚处理
	 * @param rightAssoc 结合方式，右结合
	 */
	void newBinaryOp(String token, int priority, BinaryOp parser, boolean rightAssoc);
	//endregion
	//region 运算符重载API
	@FunctionalInterface
	interface ExprOp { ExprNode test(LocalContext ctx, OperatorContext opctx, ExprNode left, Object right);}
	interface OperatorContext {
		short symbol();
		IType leftType();
		IType rightType();
	}

	/**
	 * 为已存在的一元运算符指定方法
	 * 参考: !"" => "".isEmpty()
	 * @param operator 运算符 可以是任意字符串 如果使用特殊符号之外的，请自行处理LiteralEnd
	 * @param type 该运算符适配的类型
	 * @param node 调用的方法 可以是静态的
	 * @param side bitset bit1:左侧(UnaryPre) bit2:右侧(UnaryPost)
	 */
	void onUnary(String operator, Type type, MethodNode node, int side);
	/**
	 * 为已存在的二元运算符指定方法
	 * 参考: "awsl" * 10 => "awsl".repeat(10)
	 * 参考: 5 ** 3      => Math.pow(5, 3)
	 *
	 * @param left 该运算符左侧的类型
	 * @param operator 运算符 可以是任意字符串 如果使用特殊符号之外的，请自行处理LiteralEnd
	 * @param right 该运算符右侧的类型
	 * @param node 调用的方法
	 * @param swap 若为true，并且node是静态的，那么在不匹配时，允许翻转left和right
	 */
	void onBinary(Type left, String operator, Type right, MethodNode node, boolean swap);
	/**
	 * 自己处理运算符
	 * @param operator 运算符 可以是任意字符串 如果使用特殊符号之外的，请自行处理LiteralEnd
	 * @param resolver
	 */
	void addOpHandler(String operator, ExprOp resolver);
	//endregion

	/**
	 * 添加hook以便转换链式方法调用
	 * @param existInRuntime
	 */
	StreamChain newStreamChain(String type, boolean existInRuntime, Consumer<StreamChainExpr> parser, @Nullable Type realType);
}