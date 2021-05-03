package roj.reflect.misc;

import roj.reflect.J8Util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/1 15:26
 */
final class Runtime {
    static final int VM_ARCH_DATA_MODEL = getArchDataModel();
    static final long OBJECT_HEADER_SIZE = J8Util.getObjectHeaderSize();

    private static int getArchDataModel() {
        String str = System.getProperty("sun.arch.data.model");
        int model;
        try {
            model = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            model = -1;
        }
        return model;
    }
}
