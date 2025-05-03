package roj.asmx.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/3/21 13:44
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface Cancellable {}