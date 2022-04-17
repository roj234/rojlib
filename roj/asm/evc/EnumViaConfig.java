package roj.asm.evc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj233
 * @since 2022/4/29 18:06
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface EnumViaConfig {
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.CONSTRUCTOR)
    @interface Constructor {
        String[] value();
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.FIELD)
    @interface Holder {
        String value();
        boolean optional() default false;
    }
}
