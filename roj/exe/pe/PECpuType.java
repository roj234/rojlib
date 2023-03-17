package roj.exe.pe;

import java.lang.reflect.Field;

/**
 * @author Roj233
 * @since 2022/1/18 19:51
 */
public class PECpuType {
	public static final int UNKNOWN = 0, AM33 = 467, AMD64 = 34404, ARM = 448, ARMV7 = 452, ARM64 = 43620, EBC = 3772, I386 = 332, IA64 = 512, M32R = 36929, MIPS16 = 614, MIPSFPU = 870, MIPSFPU16 = 1126, POWERPC = 496, POWERPCFP = 497, R4000 = 358, SH3 = 418, SH3DSP = 419, SH4 = 422, SH5 = 424, THUMB = 450, WCEMIPSV2 = 361;

	public static String toString(char type) {
		for (Field field : PECpuType.class.getDeclaredFields()) {
			try {
				if (field.getInt(null) == type) {
					return field.getName();
				}
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return "UNKNOWN";
	}
}
