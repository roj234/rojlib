package roj.exe.elf;

import roj.util.ByteList;

/**
 * Program Header / Executable Section
 *
 * @author Roj233
 * @since 2022/5/30 17:34
 */
public class ElfSectionE implements ElfSegment {
	public static final int SIZE = 32;

	public int type;
	public int offset;
	public int virtualAddress;
	public int physicAddress;
	public int fileSize;
	public int memorySize;
	public int flags;
	public int align;

	public void toByteArray(ElfFile owner, ByteList w) {
		w.putInt(type).putInt(offset).putInt(virtualAddress).putInt(physicAddress).putInt(fileSize).putInt(memorySize).putInt(flags).putInt(align);
	}

	public void fromByteArray(ElfFile owner, ByteList r) {
		type = r.readInt();
		offset = r.readInt();
		virtualAddress = r.readInt();
		physicAddress = r.readInt();
		fileSize = r.readInt();
		memorySize = r.readInt();
		flags = r.readInt();
		align = r.readInt();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(1024);

		sb.append("PE公共头部");
		return sb.toString();
	}
}