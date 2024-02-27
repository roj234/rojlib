package roj.net.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/2/5 0005 11:35
 * 方法签名: [Request, [ResponseHeader]][请求参数]
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Route {
	/**
	 * 留空以使用 [方法名].replace('__', '/')
	 * <p>
	 * 参考：<a href="https://router.vuejs.org/zh/guide/essentials/route-matching-syntax.html">路由的匹配语法</a><br>
	 *简易参考
	 * <table><tr><td>
	 * /some/:param/path
	 * <td>
	 * 匹配/some/任意/path，并将param注入方法的同名参数 (WIP 暂时通过Headers提供参数)
	 * <tr><td>
	 * /some/:param([regexp])
	 * <td>
	 * 参数param必须同时满足括号中的正则表达式
	 * <tr><td>
	 * /some/:param*
	 * <td>
	 * 参数param可以出现零至多次，使用+号让它能出现一至多次，使用?号让它能出现零或一次，方法的同名参数必须是数组
	 * </table>
	 * <p>三种格式同时使用时，括号必须在*+?之前出现
	 * <p>默认情况下，所有路由是<i>区分</i>大小写的，并且<i>不</i>严格匹配尾部斜线。
	 * <p>方法参数类型可以不是String，还可以是基本类型，OKRouter会调用对应的parseXXX函数
	 * <p>使用正则路由而不是代码来限制参数，可能对性能有负面影响</p>
	 */
	String value() default "啊随便写点什么反正我也不会用Proxy访问注解的";
	boolean prefix() default false;
}