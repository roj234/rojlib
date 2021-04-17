package roj.reflect.misc;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/1 15:19
 */
public class Unsafe {
    public static long objectAddress(Object ptr) {
        switch (Runtime.VM_ARCH_DATA_MODEL) {
            case 32:
                return n32Addr(ptr);
            case 64:
                return n64Addr(ptr);
            default:
                return -1;
        }
    }

    private static long n32Addr(Object ptr) {
        return 0;
    }

    private static long n64Addr(Object ptr) {
        return 0;
    }

}
