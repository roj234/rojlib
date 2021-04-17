package roj.reflect.misc;

import roj.reflect.J8Util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/1 15:26
 */
public class Runtime {
    public static final int VM_ARCH_DATA_MODEL;
    public static final long OBJECT_HEADER_SIZE;
    public static final String VM_NAME, OS_ARCH;

    static {
        VM_ARCH_DATA_MODEL = getArchDataModel();
        VM_NAME = getVMName();
        OS_ARCH = getOSArch();
        OBJECT_HEADER_SIZE = J8Util.getObjectHeaderSize();
    }

    private static int getArchDataModel() {
        String archDataModelStr = System.getProperty("sun.arch.data.model");
        int archDataModel;
        try {
            archDataModel = Integer.parseInt(archDataModelStr);
        } catch (NumberFormatException nfe) {
            archDataModel = -1;
        }
        return archDataModel;
    }

    private static String getVMName() {
        return System.getProperty("java.vm.name");
    }

    private static String getOSArch() {
        return System.getProperty("os.arch");
    }

    public static boolean isHotSpotVM() {
        return VM_NAME != null && VM_NAME.contains("HotSpot");
    }

    public static boolean isJRockitVM() {
        return VM_NAME != null && VM_NAME.contains("JRockit");
    }
}
