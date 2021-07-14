package lac.server.note;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可以将随机数放在哪里
 *
 * @author Roj233
 * @since 2021/7/8 23:46
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface RandomInject {
    String[] ids();
    long[] value();
    byte[] type(); // NativeType.X for LDC tag or -1 for push finder
}
