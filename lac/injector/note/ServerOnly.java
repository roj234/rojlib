package lac.injector.note;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ServerOnly
 *
 * @author Roj233
 * @since 2021/7/9 0:13
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface ServerOnly {
    String client() default "";
}
