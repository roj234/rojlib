package roj.http.server.auto;

import org.intellij.lang.annotations.MagicConstant;
import roj.http.server.Request;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines an HTTP route in the {@link OKRouter} framework.
 * Annotate methods to turn them into request handlers. Defaults to allowing GET and POST methods.
 * The route path uses method name with "__" replaced by "/". Supports advanced path matching syntax
 * inspired by Vue Router, including parameters, regex constraints, optional/repeatable segments,
 * and wildcards.
 *
 * <p><strong>Basic Example:</strong></p>
 * <pre>{@code
 * @Route("/users/:id")
 * public User getUser(Request req, @RequestParam("id") int id) { ... }
 * }</pre>
 *
 * <p><strong>方法参数:</strong> {@code [Request req], [Response response], [params...]} 前二者可以省略，但顺序不能改动.</p>
 *
 * <p>Use {@link Accepts} for custom HTTP methods, {@link Mime} for response types,
 * and {@link Interceptor} for pre-processing.</p>
 *
 * <h3>路径匹配语法</h3>
 * <table>
 *   <tr><th>Example</th><th>Description</th><th>Type</th></tr>
 *   <tr><td>{@code /user/:id}</td><td>Matches "/user/123", injects id="123" (use {@link Request#argument(String)} or annotations)</td><td>Parameter Injection</td></tr>
 *   <tr><td>{@code /:lang(en|zh)/index}</td><td>{@code lang} accepts only "en" or "zh" (regex constraint)</td><td>Regex Constraint</td></tr>
 *   <tr><td>{@code /list/:page?}</td><td>{@code page} is optional (? = 0-1 times)</td><td>Quantifier</td></tr>
 *   <tr><td>{@code /images/**}</td><td>Matches "/images/2024/logo.png"</td><td>Wildcard Prefix</td></tr>
 * </table>
 *
 * <p><h3>备注</h3>
 * <ol>
 *     <li>路径是否以{@code /}开始无任何影响，它们在解析时相同对待</li>
 *     <li>正则约束的圆括号和前缀通配符的{@code /**}是固定句式</li>
 *     <li>正则约束必须在次数约束之前, 这也是固定句式</li>
 *     <li>{@code ?}限制出现零或一次，{@code +}限制一至多次，{@code *}限制零至多次；使用次数约束时，不建议使用参数注入，除非你很了解其规则</li>
 *     <li>使用正则而不是代码约束参数，可能会影响性能</li>
 *     <li>优先级：精确路径 > 正则约束 > 无约束 > ? > + > * > 前缀通配符</li>
 *     <li>除了上面两种办法，你还可用{@link Request#arguments()}获取所有请求参数</li>
 *     <li>通过{@link #strict()}开关严格模式
 *       <ul>
 *         <li>基础规则: {@code Route("/admin/")} 只匹配 {@code /admin/}.</li>
 *         <li>严格模式(默认): {@code Route("/user")} 只匹配 {@code /user}.</li>
 *         <li>宽松模式: {@code Route("/user")} 匹配 {@code /user} and {@code /user/} ({@link Request#path()} = "" or "/").</li>
 *       </ul>
 *     </li>
 * </ol>
 *
 *
 * @author Roj234
 * @since 2023/2/5 11:35
 * @see Accepts
 * @see Request#path()
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Route {
	/**
	 * The route path pattern. If empty, uses method name.replace("__", "/").
	 * Full syntax documentation: <a href="https://router.vuejs.org/zh/guide/essentials/route-matching-syntax.html">Vue Router Guide</a>.
	 *
	 * @see Request#argument(String)
	 * @see Request#arguments()
	 * @see #strict()
	 */
	String value() default "啊随便写点什么反正我也不会用Proxy访问注解的";
	/**
	 * If {@code true}, enables strict mode: non-trailing-slash routes do not match trailing-slash paths.
	 *
	 * @return whether strict mode is enabled
	 */
	boolean strict() default true;
	/**
	 * 无注解的方法参数的值从何处获取.
	 * 合理的值是POST或GET，但COOKIE、PARAM也可用
	 * {@link GET} 会将默认值设置为GET.
	 * {@link POST} 会设置为POST.
	 * 这些的优先级低于手动指定
	 * @return the source for deserialization
	 */
	@MagicConstant(stringValues = {"POST", "GET", "COOKIE", "PARAM"})
	String deserializeFrom() default "";
}