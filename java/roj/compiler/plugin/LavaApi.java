package roj.compiler.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Attributed;
import roj.asm.ClassNode;
import roj.asm.IClass;
import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.attr.InnerClasses;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asmx.AnnotationRepo;
import roj.collect.ToIntMap;
import roj.compiler.Tokens;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.PrefixOperator;
import roj.compiler.context.Library;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.stc.StreamChain;
import roj.compiler.plugins.stc.StreamChainExpr;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;
import roj.config.ParseException;
import roj.config.Word;
import roj.util.TypedKey;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/2/20 0020 16:57
 */
public interface LavaApi {
	/**
	 * 某个编译器预定义特性{@link roj.compiler.LavaFeatures}是否开启
	 */
	boolean hasFeature(int specId);

	/**
	 * 通过全限定名获取类结构
	 * @param name 类全限定名（使用'/'分隔，如java/lang/String）
	 */
	@Nullable ClassNode getClassInfo(CharSequence name);
	/**
	 * 将库添加到编译器类路径
	 * @param library 要添加的库对象
	 * @implNote 与某些实现(javac)不同，此方法不会导致库中的代码被执行
	 */
	void addLibrary(Library library);

	@NotNull ResolveHelper getResolveHelper(@NotNull IClass info);
	/**
	 * 修改指定类结构后，使指定类的ResolveHelper失效，并在下一次getResolveHelper中重新生成
	 * @param info 发生结构变化的类信息
	 * @apiNote 不是线程安全的，最好别用
	 */
	void invalidateResolveHelper(IClass info);
	/**
	 * 获取类继承层级信息
	 * 由于Java并没有给这些自定义类型只读的包装器 禁止修改返回值！
	 * 值的高16位存放递增的index{@link LocalContext#getCommonParent(IClass, IClass)}，低16位存放info类到key类需要向上转型的最低次数
	 * @param info 目标类信息
	 */
	@NotNull ToIntMap<String> getHierarchyList(IClass info);
	/**
	 * 获取方法重载列表，不存在时返回{@link ComponentList#NOT_FOUND}
	 */
	@NotNull ComponentList getMethodList(IClass info, String name);
	/**
	 * 获取字段重载列表，不存在时返回{@link ComponentList#NOT_FOUND}
	 */
	@NotNull ComponentList getFieldList(IClass info, String name);
	/**
	 * 获取泛型类型参数的实际类型
	 * @apiNote 最好用这个：{@link LocalContext#inferGeneric(IType, String)}
	 * @param superType 父类/接口的全限定名
	 * @return 类型参数列表，当superType不是info的父类/接口时返回null
	 * @throws ClassNotFoundException 当superType不存在时抛出
	 */
	@Nullable List<IType> getTypeArgumentsFor(IClass info, String superType) throws ClassNotFoundException;
	/**
	 * 获取内部类元信息
	 * @param info 外层类元数据（不可为null）
	 * @return 内部类名到元信息的不可变映射（键为全限定名，或以!开头的简单类名，只包含具名类，不包含匿名类或引用）
	 */
	@NotNull Map<String, InnerClasses.Item> getInnerClassInfo(IClass info);

	/**
	 * 根据短名称查找可能存在的包路径
	 * @param shortName 短名称，例如"List"
	 * @return 包含完整包路径的候选列表, 如["java/lang"]，或空列表
	 */
	List<String> getAvailablePackages(String shortName);

	void report(IClass source, Kind kind, int pos, String code);
	void report(IClass source, Kind kind, int pos, String code, Object... args);

	// 沙盒类加载器
	/**
	 * 添加沙盒白名单
	 *
	 * 默认的包白名单(不继承): "java.lang", "java.util", "java.util.regex", "java.util.function", "java.text", "roj.compiler", "roj.text", "roj.config.data"
	 * 默认的类黑名单: "java.lang.Process", "java.lang.ProcessBuilder", "java.lang.Thread", "java.lang.ClassLoader"
	 * @param packageOrTypename 包名（如"java/util"）或全限定类名（如"java/lang/String"）
	 * @param childInheritance 是否递归应用于子包
	 * @apiNote 对全限定类名应用继承的结果是未定义的
	 */
	void addSandboxWhitelist(String packageOrTypename, boolean childInheritance);
	/**
	 * 添加沙盒黑名单（优先级高于白名单）
	 */
	void addSandboxBlacklist(String packageOrTypename, boolean childInheritance);
	/**
	 * 实例化沙盒环境中的类
	 * @param data 类字节码结构
	 * @throws NoClassDefFoundError 受策略限制无法加载某些类时
	 */
	Object createSandboxInstance(ClassNode data) throws NoClassDefFoundError;
	/**
	 * 向沙盒环境中注册自定义类，不加载。
	 */
	void addSandboxClass(String className, byte[] data);
	/**
	 * 加载沙盒环境中的指定类。
	 * @see ClassLoader#loadClass(String, boolean)
	 */
	Class<?> loadSandboxClass(String className, boolean resolve);

	// 存放类型安全的任意对象，不是线程安全的
	<T> T attachment(TypedKey<T> key);
	<T> T attachment(TypedKey<T> key, T val);

	//region 注解处理API
	/**
	 * 注册自定义注解处理器
	 *
	 * <p>支持处理的元素类型及处理器参数说明：
	 * <ul>
	 *   <li><b>类元素</b>（包括TYPE, ANNOTATION_TYPE, PACKAGE, MODULE）
	 *     <ul>
	 *           <li>{@code file}参数类型(后续相同)：
	 *             <ul>
	 *               <li>编译单元：{@link roj.compiler.context.CompileUnit}</li>
	 *               <li>classpath类：{@link roj.asm.ClassView}</li>
	 *             </ul>
	 *           </li>
	 *           <li>{@code node}参数类型与{@code file}参数类型一致</li>
	 *     </ul>
	 *   </li>
	 *
	 *   <li><b>字段（FIELD）</b>
	 *     <ul>
	 *       <li>{@code node}参数类型：{@link roj.asm.FieldNode}</li>
	 *     </ul>
	 *   </li>
	 *
	 *   <li><b>方法（METHOD）</b>
	 *     <ul>
	 *       <li>{@code node}参数类型：{@link roj.asm.MethodNode}</li>
	 *     </ul>
	 *   </li>
	 *
	 *   <li><b>方法参数（METHOD_PARAMETER）</b>
	 *     <ul>
	 *       <li>{@code node}参数类型：
	 *         <ul>
	 *           <li>编译单元：{@link roj.compiler.asm.ParamAnnotationRef}</li>
	 *           <li><i>classpath类：暂未实现</i></li>
	 *         </ul>
	 *       </li>
	 *     </ul>
	 *   </li>
	 *
	 *   <li><b>类型使用（TypeUse）</b>
	 *     <ul>
	 *       <li><i>暂未实现</i></li>
	 *     </ul>
	 *   </li>
	 * </ul>
	 *
	 * <p><b>注意事项：</b>
	 * <ul>
	 *   <li>标注"暂未实现"的功能可能在后续版本中支持</li>
	 *   <li>处理classpath中的方法参数注解时可能抛出未支持异常</li>
	 * </ul>
	 *
	 * @see Processor#handle(LocalContext, IClass file, Attributed node, Annotation)
	 */
	void addAnnotationProcessor(Processor processor);

	/**
	 * 获取classpath中所有注解。
	 * <p><b>性能警告：</b>此方法在初次调用时会扫描整个类路径，非常耗时。</p>
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
		 * 当 {@link Library} 中的类首次被加载到全局上下文时触发。
		 * <p>典型用途：
		 * <ul>
		 *   <li>动态修改类的方法实现（例如 AOP）</li>
		 *   <li>作为类加载限制的兜底措施（虽然我觉得{@link roj.compiler.resolve.ImportList#setRestricted(boolean) 包导入限制}足够了）</li>
		 * </ul>
		 *
		 * @return 修改后的类节点（可返回或修改原对象）
		 */
		ClassNode classResolved(ClassNode info);
	}

	/**
	 * 添加库解析监听器。
	 * @param priority 优先级（数值越小优先级越高）
	 */
	void addResolveListener(int priority, Resolver resolver);
	//endregion
	//region 词法分析API
	/**
	 * 创建具有指定分类的新词法标记。
	 *
	 * @param token 标记名称（允许包含空格和Unicode字符，需唯一）
	 * @param tokenCategory 标记上下文分类（使用位掩码组合下列常量：
	 *                      <ul>
	 *                        <li>{@link Tokens#STATE_CLASS} - 允许在类/接口声明上下文出现</li>
	 *                        <li>{@link Tokens#STATE_MODULE} - 允许在模块声明上下文出现</li>
	 *                        <li>{@link Tokens#STATE_EXPR} - 允许在表达式上下文出现</li>
	 *                        <li>{@link Tokens#STATE_TYPE} - 允许在类型声明上下文出现</li>
	 *                      </ul>
	 *                      例如 {@code STATE_CLASS | STATE_MODULE} 表示同时适用于两种上下文）
	 * @return 分配的全局唯一标记ID（正整数），可用于后续词法解析和语法分析
	 */
	int tokenCreate(String token, int tokenCategory);

	/**
	 * 为现有标记注册等义词法表示。
	 * <p>例如将中文分号「；」映射到英文分号{@code ;}，可与{@link #tokenLiteralEnd}结合打造属于你的中文编程语言（bushi<br>
	 * 所有别名在词法分析阶段将被视为原始标记。
	 *
	 * @param token 目标标记ID（需通过{@link #tokenCreate}预先创建，或使用系统预定义ID）
	 * @apiNote 该方法不适用于动态标记（如{@link Word#FLOAT}等字面量类型）
	 * <p>不当调用可能导致词法分析阶段未定义行为
	 */
	void tokenAlias(int token, String alias);

	/**
	 * 注册字面量解析终止符。
	 * <p>当解析数值/字符/字符串等字面量时，遇到注册字符将立即终止当前字面量解析。
	 * 例如注册{@code '_'}后，数字字面量{@code 123_456}将被解析为{@code 123}和{@code _456}两个token。
	 *
	 * @param lend 终止符（长度必须为1，重复注册无效）
	 */
	void tokenLiteralEnd(String lend);
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
	interface StartOp { Expr parse(LocalContext ctx) throws ParseException;}

	@FunctionalInterface
	interface ContinueOp<T extends Expr> { T parse(LocalContext ctx, T node) throws ParseException;}

	@FunctionalInterface
	interface BinaryOp { Expr parse(LocalContext ctx, Expr left, Expr right);}

	/**
	 * 新增前缀运算符, 后缀可以直接使用TerminalOp
	 */
	void newUnaryOp(String token, ContinueOp<PrefixOperator> parser);

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
	void newContinueOp(String token, ContinueOp<Expr> parser);
	/**
	 * 新增值终止运算符
	 */
	void newTerminalOp(String token, ContinueOp<Expr> parser);
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
	interface ExprOp { Expr test(LocalContext ctx, OperatorContext opctx, Expr left, Object right);}
	interface OperatorContext {
		short symbol();
		IType leftType();
		IType rightType();
	}

	/**
	 * 为已存在的一元运算符指定方法
	 * 参考: !"" => "".isEmpty()
	 * @param operator 运算符 可以是任意字符串 如果不使用特殊符号，可能需要管理{@link #tokenLiteralEnd(String) LiteralEnd}
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
	 * @param operator 运算符 可以是任意字符串 如果不使用特殊符号，可能需要管理{@link #tokenLiteralEnd(String) LiteralEnd}
	 * @param right 该运算符右侧的类型
	 * @param node 调用的方法
	 * @param swap 是否允许交换左右操作数（当 node 是静态方法时有效）
	 */
	void onBinary(Type left, String operator, Type right, MethodNode node, boolean swap);
	/**
	 * 自定义运算符处理逻辑
	 * @param operator 运算符 可以是任意字符串 如果不使用特殊符号，可能需要管理{@link #tokenLiteralEnd(String) LiteralEnd}
	 */
	void addOpHandler(String operator, ExprOp resolver);
	//endregion
	/**
	 * 创建新的流式处理链
	 * @param type 链式调用的入口类型
	 * @param existInRuntime type是否在运行时存在 （虚拟类型/真实类型）
	 * @param realType 运行时实际类型（可选）
	 */
	StreamChain newStreamChain(String type, boolean existInRuntime, Consumer<StreamChainExpr> parser, @Nullable Type realType);
}