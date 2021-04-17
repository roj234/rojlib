package roj.asm.nixim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 重映射方法 (Overwrite or inject at HEAD or TAIL)
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface RemapTo {
    /**
     * @return target method name
     */
    String value();

    boolean ignoreParam() default false;

    /**
     * 头尾都可以用SIJ
     * 如果要插在中间就找到这个位置
     */
    int injectPos() default -1;

    byte codeAtPos() default 0;

    boolean useSuperInject() default true;

    boolean mustHit() default true;

    /**
     * 上级没有覆盖
     */
    boolean isRemapCopy() default false;
}
