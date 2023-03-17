package roj.exe.elf;

import java.lang.reflect.Field;

/**
 * @author Roj233
 * @since 2022/1/18 19:51
 */
public class ElfCpuType {
	public static final int EM_NONE = 0, EM_M32 = 1, EM_SPARC = 2, EM_386 = 3, EM_68K = 4, EM_88K = 5, EM_486 = 6, /* Perhaps disused */
		EM_860 = 7, EM_MIPS = 8,/* MIPS R3000 (officially, big-endian only) */
		EM_MIPS_RS4_BE = 10,/* MIPS R4000 big-endian */
		EM_PARISC = 15,/* HPPA */
		EM_SPARC32PLUS = 18,/* Sun's "v8plus" */
		EM_PPC = 20,/* PowerPC */
		EM_PPC64 = 21, /* PowerPC64 */
		EM_ARM = 40,/* ARM */
		EM_SH = 42,/* SuperH */
		EM_SPARCV9 = 43,/* SPARC v9 64-bit */
		EM_IA_64 = 50,/* HP/Intel IA-64 */
		EM_X86_64 = 62,/* AMD x86-64 */
		EM_S390 = 22,/* IBM S/390 */
		EM_CRIS = 76,/* Axis Communications 32-bit embedded processor */
		EM_V850 = 87,/* NEC v850 */
		EM_H8_300H = 47,/* Hitachi H8/300H */
		EM_H8S = 48,/* Hitachi H8S */
	/*
	 * This is an interim value that we will use until the committee comes
	 * up with a final number.
	 */
	EM_ALPHA = 0x9026, /* Bogus old v850 magic number, used by old tools.*/
	EM_CYGNUS_V850 = 0x9080, /*
	 * This is the old interim value for S/390 architecture
	 */
	EM_S390_OLD = 0xA390;

	public static String toString(char type) {
		for (Field field : ElfCpuType.class.getDeclaredFields()) {
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