package lac.server.note;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字符串加密
 *
 * @author Roj233
 * @since 2021/7/8 23:46
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface EncodeString {
    String[] value();
    String[] encodeType();
}
