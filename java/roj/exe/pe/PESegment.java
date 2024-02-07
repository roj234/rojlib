package roj.exe.pe;

import roj.util.ByteList;

/**
 * @author Roj233
 * @since 2022/1/18 19:45
 */
public interface PESegment {
	void toByteArray(PEFile owner, ByteList w);

	void fromByteArray(PEFile owner, ByteList r);
}
