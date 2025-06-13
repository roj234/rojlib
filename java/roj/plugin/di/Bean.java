package roj.plugin.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 依赖注入单例(WIP)
 * @author Roj234
 * @since 2025/07/16 2:50
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Bean {}
