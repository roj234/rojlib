package roj.kscript.api;

import roj.kscript.type.KType;

import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/14 22:45
 */
@FunctionalInterface
public interface NativeMethod {
    NativeMethod NULL = args -> null;

    KType handle(List<KType> args);
}
