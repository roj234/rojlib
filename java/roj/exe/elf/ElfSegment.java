package roj.exe.elf;

import roj.util.ByteList;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/1/18 19:45
 */
public interface ElfSegment {
	void toByteArray(ElfFile owner, ByteList w) throws IOException;

	void fromByteArray(ElfFile owner, ByteList r) throws IOException;
}