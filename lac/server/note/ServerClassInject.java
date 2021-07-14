package lac.server.note;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 替换某些class
 *
 * @author Roj233
 * @since 2021/7/9 0:01
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface ServerClassInject {
    String dest();
    boolean onlyChild() default false;
    String[] child() default "";
    String[] childTo() default "";
}
