package roj.compiler.plugin;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.JavaLexer;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.UnaryPreNode;
import roj.compiler.context.LocalContext;
import roj.compiler.plugins.stc.StreamChain;
import roj.compiler.plugins.stc.StreamChainExpr;

import java.util.function.BiFunction;
import java.util.function.Consumer;

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
 * @author Roj234
 * @since 2024/2/20 0020 1:31
 */
public interface ExprApi {
	/**
	 * 新增前缀一元运算符
	 */
	void addUnaryPre(String token, BiFunction<JavaLexer, @Nullable UnaryPreNode, UnaryPreNode> fn);

	/**
	 * 新增表达式生成运算符<br>
	 * 该运算符自身就是一个完整的表达式<br>
	 * 值模式流程：(生成 -> 转换 -> 终止)
	 */
	void addExprGen(String token, LEG fn);

	/**
	 * 新增值生成运算符
	 * 参考: &lt;minecraft:stone&gt;
	 */
	void addExprStart(String token, LEG fn);
	/**
	 * 新增值转换运算符
	 */
	void addExprConv(String token, LEC fn);
	/**
	 * 新增值终止运算符
	 */
	void addExprTerminal(String token, LEC fn);

	/**
	 * 新增一个二元运算符
	 * @param token 运算符 如果重复会报错
	 * @param priority 运算符优先级，不加括号时，越大越晚处理
	 */
	void addBinary(String token, int priority, LEC callback);

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


	interface ExprOp { ExprNode test(LocalContext ctx, OperatorContext opctx, ExprNode left, Object right);}
	interface OperatorContext {
		short symbol();
		IType leftType();
		IType rightType();
	}

	/**
	 * 添加hook以便转换链式方法调用
	 * @param allowFallback
	 */
	StreamChain newStreamChain(String chainType, boolean allowFallback, Consumer<StreamChainExpr> callback, Type tmpType);
}