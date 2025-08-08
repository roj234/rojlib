package roj.asmx.injector;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法调用重定向
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Redirect {
	/**
	 * 目标方法名称
	 * @return '/'表示使用当前方法名，空字符串表示由{@code NiximHelper}决定
	 */
	String value() default "";

	/**
	 * 目标方法签名（必须显式指定）
	 * 例如 "(J)V"
	 */
	String injectDesc();

	/**
	 * 配置标志位
	 * 与{@link Inject#flags()}相同
	 */
	@MagicConstant(flagsFromClass = Inject.class)
	int flags() default 0;

	/**
	 * javap格式的方法匹配描述符
	 * 例如: "java/lang/Object.<init>()V"（类名可省略）
	 *
	 * <h3>被注解方法签名验证规则：</h3>
	 * <ol>
	 *   <li>非静态方法：需完全匹配签名，并且描述符的方法必须在目标类中</li>
	 *   <li>静态方法：需在签名开头添加描述符的类名（省略时填写运行时的值）</li>
	 *   <li>附加参数：在静态方法签名后追加目标类类型，会加一个ALOAD_0调用进去</li>
	 * </ol>
	 */
	String matcher();

	/**
	 * 替换哪几个匹配
	 * @return 空数组表示替换所有匹配，[0,2]表示替换第1和第3个匹配
	 */
	int[] occurrences() default {};
}