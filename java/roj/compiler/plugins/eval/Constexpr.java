package roj.compiler.plugins.eval;

import roj.compiler.runtime.RtUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 常量 <br>
 * 放在字段：如果该字段是基本类型数组，并且是编译期常量，那么使用{@link RtUtil#pack(byte[]) Base128}压缩它 <br>
 * 上述功能未实现，目前你可以在静态初始化中使用<code>QuickUtil.unpackB(QuickUtils.base128(编译期常量数组))</code>来实现<br>
 * 放在方法：如果入参是常量，则在编译时执行该方法 <br>
 * 放在类：添加这个类到编译沙盒中 <p>
 * <i>带有该注解的方法可能会被编译两次，有轻微的性能问题</i> <p>
 * 有关编译沙盒的白名单，请看{@link Sandbox#allowed}
 * @author Roj233
 * @since 2021/9/2 21:52
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface Constexpr {
	/**
	 * 尚未实现
	 */
	String[] classes() default {};
}