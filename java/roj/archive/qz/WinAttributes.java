package roj.archive.qz;

import roj.plugins.ci.annotation.CompileOnly;

/**
 * @author Roj234
 * @since 2025/06/05 05:20
 */
@CompileOnly
public interface WinAttributes {
	int FILE_ATTRIBUTE_READONLY         = 0x00000001;
	int FILE_ATTRIBUTE_HIDDEN           = 0x00000002;
	int FILE_ATTRIBUTE_SYSTEM           = 0x00000004;
	int FILE_ATTRIBUTE_DIRECTORY        = 0x00000010;
	int FILE_ATTRIBUTE_ARCHIVE          = 0x00000020;
	int FILE_ATTRIBUTE_DEVICE           = 0x00000040;
	int FILE_ATTRIBUTE_NORMAL           = 0x00000080;
	int FILE_ATTRIBUTE_REPARSE_POINT    = 0x00000400;
}
