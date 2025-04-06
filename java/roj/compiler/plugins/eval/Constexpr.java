package roj.compiler.plugins.eval;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 如果入参是常量，则在编译时执行该方法 <p>
 * 在沙盒中执行，只包含极其有限的类，可以通过命令行参数添加白名单 <p>
 * <i>带有该注解的方法可能会被编译两次</i>
 * @author Roj233
 * @since 2021/9/2 21:52
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface Constexpr {}