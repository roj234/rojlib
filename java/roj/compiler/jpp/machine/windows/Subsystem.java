package roj.compiler.jpp.machine.windows;

/**
 * @author Roj233
 * @since 2022/1/18 19:51
 */
public class Subsystem {
	public static final int UNKNOWN = 0, NATIVE = 1, WINDOWS_GUI = 2, WINDOWS_CUI = 3, POSIX_CUI = 7, WINDOWS_CE_GUI = 9, EFI_APPLICATION = 10, EFI_BOOT_SERVICE_DRIVER = 11, EFI_RUNTIME_DRIVER = 12, EFI_ROM = 13, XBOX = 14;

	public static String toString(char subsystem) {
		return switch (subsystem) {
			case NATIVE -> "NATIVE";
			case WINDOWS_GUI -> "WINDOWS_GUI";
			case WINDOWS_CUI -> "WINDOWS_CUI";
			case POSIX_CUI -> "POSIX_CUI";
			case WINDOWS_CE_GUI -> "WINDOWS_CE_GUI";
			case EFI_APPLICATION -> "EFI_APPLICATION";
			case EFI_BOOT_SERVICE_DRIVER -> "EFI_BOOT_SERVICE_DRIVER";
			case EFI_RUNTIME_DRIVER -> "EFI_RUNTIME_DRIVER";
			case EFI_ROM -> "EFI_ROM";
			case XBOX -> "XBOX";
			default -> "UNKNOWN";
		};
	}
}