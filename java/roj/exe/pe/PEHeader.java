package roj.exe.pe;

import roj.text.ACalendar;
import roj.util.ByteList;

/**
 * PE头
 *
 * @author Roj233
 * @since 2022/1/18 19:39
 */
public class PEHeader implements PESegment {
	public static final int FORMAT_PE32 = 267;
	public static final int FORMAT_PE32plus = 523;
	public static final int FORMAT_ROM = 263;

	public static final char C_NO_RELOC = 0x0001;
	public static final char C_EXECUTABLE = 0x0002;
	public static final char C_NO_LINE = 0x0004;
	public static final char C_NO_LOCAL_SYM = 0x0008;
	public static final char C_AGGRESIVES_TRIM_WORKSET = 0x0010;
	public static final char C_LARGE_ADDRESS_AWARE = 0x0020;
	public static final char C_BYTES_REVERSED_LO = 0x0080; // 低位字节反转?
	public static final char C_32BIT_MACHINE = 0x0100;
	public static final char C_DEBUG_STRIPPED = 0x0200;
	public static final char C_REMOVABLE_RUN_FROM_SWAP = 0x0400; // 若在可移动设备则复制到交换文件
	public static final char C_NET_RUN_FROM_SWAP = 0x0800; // 同上，来自网络
	public static final char C_SYSTEM = 0x1000;
	public static final char C_DLL = 0x2000;
	public static final char C_UP_SYSTEM_ONLY = 0x4000; // 只能运行在UP机器?
	public static final char C_BYTES_REVERSED_HI = 0x8000; // 高位字节反转?

	public char cpuType;
	public char sectionCount;
	public int timestamp;
	public int symbolTableOffset;
	public int symbolCount;
	public char optHeaderSize;
	public char characteristics;

	// Optional header
	public char format;

	public char linkerVersion;
	public int codeSize;               // 所有代码段(section?)的总和大小,注意：必须是FileAlignment的整数倍,存在但没用
	public int initializedDataSize;
	public int uninitializedDataSize;
	public int entryPoint;             // ※程序入口地址OEP，这是一个RVA(Relative Virtual Address),通常会落在.text section,此字段对于DLLs/EXEs都适用。
	public int codeBase;
	public int dataBase;
	public long imageBase;             // ※内存镜像基址(默认装入起始地址),默认为4000H
	public int sectionAlign;           // ※内存对齐:一旦映像到内存中，每一个section保证从一个「此值之倍数」的虚拟地址开始
	public int fileAlign;              // ※文件对齐：最初是200H，现在是1000H
	public int OSVersion;
	public int imageVersion;
	public int subsystemVersion;
	public int win32Version;
	public int imageSize;              // ※PE文件在内存中映像总大小,sizeof(ImageBuffer),SectionAlignment的倍数
	public int headerSize;             // ※DOS头(64B)+PE标记(4B)+标准PE头(20B)+可选PE头+节表的总大小，按照文件对齐(FileAlignment的倍数)
	public int checksum;
	public char subsystem;
	public char DLLCharacteristic;
	public long stackReservedSize;
	public long stackCommitSize;
	public long heapReservedSize;
	public long heapCommitSize;
	public int loaderFlag;             // 总是0？？
	public int dataIndexSize;          // 总是16？？

	public long getTimestamp() {
		return timestamp * 1000L;
	}

	@Override
	public void toByteArray(PEFile owner, ByteList w) {
		w.putShortLE(cpuType)
		 .putShortLE(sectionCount)
		 .putIntLE(timestamp)
		 .putIntLE(symbolTableOffset)
		 .putIntLE(symbolCount)
		 .putShortLE(optHeaderSize)
		 .putShortLE(characteristics)
		 .putShortLE(format)
		 .putShort(linkerVersion)
		 .putIntLE(codeSize)
		 .putIntLE(initializedDataSize)
		 .putIntLE(uninitializedDataSize)
		 .putIntLE(entryPoint)
		 .putIntLE(codeBase);
		if (format == FORMAT_PE32) {
			w.putIntLE(dataBase).putIntLE((int) imageBase);
		} else {
			w.putLongLE(imageBase);
		}
		w.putIntLE(sectionAlign)
		 .putIntLE(fileAlign)
		 .putShortLE(OSVersion >>> 16)
		 .putShortLE(OSVersion)
		 .putShortLE(imageVersion >>> 16)
		 .putShortLE(imageVersion)
		 .putShortLE(subsystemVersion >>> 16)
		 .putShortLE(subsystemVersion)
		 .putIntLE(win32Version)
		 .putIntLE(imageSize)
		 .putIntLE(headerSize)
		 .putIntLE(checksum)
		 .putShortLE(subsystem)
		 .putShortLE(DLLCharacteristic);
		if (format == FORMAT_PE32) {
			w.putIntLE((int) stackReservedSize).putIntLE((int) stackCommitSize).putIntLE((int) heapReservedSize).putIntLE((int) heapCommitSize);
		} else {
			w.putLongLE(stackReservedSize).putLongLE(stackCommitSize).putLongLE(heapReservedSize).putLongLE(heapCommitSize);
		}
		w.putIntLE(loaderFlag).putIntLE(dataIndexSize);
		if (loaderFlag != 0 || dataIndexSize != 16) throw new AssertionError("Precondition from CSDN failed");
	}

	@Override
	public void fromByteArray(PEFile owner, ByteList r) {
		cpuType = (char) r.readUShortLE();
		sectionCount = (char) r.readUShortLE();
		timestamp = r.readIntLE();
		symbolTableOffset = r.readIntLE();
		symbolCount = r.readIntLE();
		optHeaderSize = (char) r.readUShortLE();
		characteristics = (char) r.readUShortLE();
		format = (char) r.readUShortLE();
		linkerVersion = r.readChar();
		codeSize = r.readIntLE();
		initializedDataSize = r.readIntLE();
		uninitializedDataSize = r.readIntLE();
		entryPoint = r.readIntLE();
		codeBase = r.readIntLE();
		if (format == FORMAT_PE32) {
			dataBase = r.readIntLE();
			imageBase = r.readUIntLE();
		} else {
			imageBase = r.readLongLE();
		}
		sectionAlign = r.readIntLE();
		fileAlign = r.readIntLE();
		OSVersion = (r.readUShortLE() << 16) | r.readUShortLE();
		imageVersion = (r.readUShortLE() << 16) | r.readUShortLE();
		subsystemVersion = (r.readUShortLE() << 16) | r.readUShortLE();
		win32Version = r.readIntLE();
		imageSize = r.readIntLE();
		headerSize = r.readIntLE();
		checksum = r.readIntLE();
		subsystem = (char) r.readUShortLE();
		DLLCharacteristic = (char) r.readUShortLE();
		if (format == FORMAT_PE32) {
			stackReservedSize = r.readUIntLE();
			stackCommitSize = r.readUIntLE();
			heapReservedSize = r.readUIntLE();
			heapCommitSize = r.readUIntLE();
		} else {
			stackReservedSize = r.readLongLE();
			stackCommitSize = r.readLongLE();
			heapReservedSize = r.readLongLE();
			heapCommitSize = r.readLongLE();
		}
		loaderFlag = r.readIntLE();
		dataIndexSize = r.readIntLE();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(1024);

		sb.append("PE公共头部")
		  .append("\n  目标CPU:     ")
		  .append(PECpuType.toString(cpuType))
		  .append("\n  区段:        ")
		  .append((int) sectionCount)
		  .append("\n  时间戳:      ")
		  .append(ACalendar.toLocalTimeString(getTimestamp()))
		  .append("\n  符号表:      0x")
		  .append(Integer.toHexString(symbolTableOffset))
		  .append(" @ Size=")
		  .append(symbolCount & 0xFFFFFFFFL)
		  .append("\n  可选头大小:  ")
		  .append((int) optHeaderSize)
		  .append("\n  标志:        ")
		  .append(Integer.toBinaryString(characteristics))
		  .append("\nPE可选头部");

		sb.append("\n  类型:        0x")
		  .append(Integer.toHexString(format))
		  .append(" (")
		  .append(formatToString(format))
		  .append(")")
		  .append("\n  链接器版本:  ")
		  .append(linkerVersion >> 8)
		  .append('.')
		  .append(0xFF & linkerVersion)
		  .append("\n  代码大小:    ")
		  .append(codeSize)
		  .append("\n  定义数据大小:")
		  .append(initializedDataSize)
		  .append("\n  空白数据大小:")
		  .append(uninitializedDataSize)
		  .append("\n  入口点:      0x")
		  .append(Integer.toHexString(entryPoint))
		  .append("\n  代码基址:    0x")
		  .append(Integer.toHexString(codeBase));
		if (format == FORMAT_PE32) {
			sb.append("\n  数据基址:    0x").append(Integer.toHexString(dataBase));
		}
		sb.append("\n  映像基址:    0x")
		  .append(Long.toHexString(imageBase))
		  .append("\n  区段对齐:    ")
		  .append(sectionAlign & 0xFFFFFFFFL)
		  .append("\n  文件对齐:    ")
		  .append(fileAlign & 0xFFFFFFFFL)
		  .append("\n  OS版本:      ")
		  .append(OSVersion >>> 16)
		  .append('.')
		  .append(OSVersion & 0xFFFF)
		  .append("\n  映像版本:    ")
		  .append(imageVersion >>> 16)
		  .append('.')
		  .append(imageVersion & 0xFFFF)
		  .append("\n  子系统版本:  ")
		  .append(subsystemVersion >>> 16)
		  .append('.')
		  .append(subsystemVersion & 0xFFFF)
		  .append("\n  映像大小:    ")
		  .append(imageSize & 0xFFFFFFFFL)
		  .append("\n  头部大小:    ")
		  .append(headerSize & 0xFFFFFFFFL)
		  .append("\n  校验码:      0x")
		  .append(Integer.toHexString(checksum))
		  .append("\n  子系统:      ")
		  .append(Subsystem.toString(subsystem))
		  .append("\n  DLL标志:     ")
		  .append(Integer.toBinaryString(DLLCharacteristic))
		  .append("\n  栈保留大小:  ")
		  .append(stackReservedSize)
		  .append("\n  栈提交大小:  ")
		  .append(stackCommitSize)
		  .append("\n  堆保留大小:  ")
		  .append(heapReservedSize)
		  .append("\n  堆提交大小:  ")
		  .append(heapCommitSize)
		  .append("\n  附加表数量:  ")
		  .append(dataIndexSize)
		  .append(" @ Size=")
		  .append(dataIndexSize << 3);
		return sb.toString();
	}

	public static String formatToString(char format) {
		switch (format) {
			case FORMAT_PE32:
				return "PE32";
			case FORMAT_PE32plus:
				return "PE32+";
			case FORMAT_ROM:
				return "ROM";
		}
		return "UNKNOWN";
	}
}