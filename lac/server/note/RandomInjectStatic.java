package lac.server.note;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @see RandomInject
 * 适用于字段
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface RandomInjectStatic {
    String[] field();
    byte[] type();
}
