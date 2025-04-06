package roj.http.server.auto;

import roj.http.server.Request;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注：默认允许POST和GET请求，手动用@Accepts指定
 * @author Roj234
 * @since 2023/2/5 0005 11:35
 * 方法签名: [Request, [ResponseHeader]][请求参数]
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Route {
	/**
	 * 路由路径定义规则（留空则使用方法名.replace('__', '/')）
	 * <p>
	 * 📖 <a href="https://router.vuejs.org/zh/guide/essentials/route-matching-syntax.html">完整语法文档</a>
	 *
	 * <p><h3>匹配规则</h3>
	 * <table>
	 *   <tr><th>示例</th><th>说明</th><th>名称</th></tr>
	 *   <tr><td>/user/:id</td><td>匹配 "/user/123"，id="123"</td><td>参数匹配</td></tr>
	 *   <tr><td>/:lang(en|zh)/index</td><td>lang 仅接受 en/zh</td><td>正则约束</td></tr>
	 *   <tr><td>/list/:page?</td><td>page参数可省略</td><td>次数约束</td></tr>
	 *   <tr><td>/images/**</td><td>匹配 "/images/2024/logo.png"</td><td>目录前缀</td></tr>
	 * </table>
	 *
	 * <p><h3>备注</h3>
	 * 1. 正则约束的圆括号和通配目录的'/..'是硬编码的结构<br>
	 * 2. ?号允许参数出现零或一次，+号允许一至多次，*号允许零至多次；使用次数约束时，不建议使用参数注入<br>
	 * 3. 正则约束必须在次数约束之前出现<br>
	 * 4. 使用正则而不是代码来限制参数的取值，也许会降低性能<br>
	 * 5. 匹配优先级：精确路径 > 正则约束的参数 > 不带约束的参数 > ?约束 > +约束 > *约束 > 目录前缀<br>
	 * 6. 使用{@link RequestParam}注解将请求参数注入到方法<br>
	 * 7. 不以斜杠结尾的路由会匹配以斜杠结尾的路径，这是为了向前兼容，你可开启严格模式
	 *
	 * <p><h3>宽松模式的斜杠匹配</h3>
	 * Route("/user") → 匹配 "/user" 和 "/user/"<br>
	 * Route("/admin/") → 匹配 "/admin/"<br>
	 * <table>
	 *   <tr><th>路径</th><th>request.path()</th></tr>
	 *   <tr><td>/user</td><td>""</td></tr>
	 *   <tr><td>/user/</td><td>"/"</td></tr>
	 *   <tr><td>/admin/</td><td>""</td></tr>
	 * </table>
	 *
	 * @see Request#argument(String)
	 * @see Request#arguments()
	 */
	String value() default "啊随便写点什么反正我也不会用Proxy访问注解的";
	@Deprecated boolean prefix() default false;
	/**
	 * 开启严格模式后，不以斜杠结尾的路由不再匹配以斜杠结尾的路径
	 */
	boolean strict() default false;
	/**
	 * 从何处注入参数（未提供注解时的默认值）
	 * 合理的值是POST或GET，但COOKIE、PARAM也可用
	 */
	String deserializeFrom() default "";
}