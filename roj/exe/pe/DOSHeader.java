package roj.exe.pe;

import roj.util.ByteList;

/**
 * @author Roj233
 * @since 2022/1/18 21:28
 */
public class DOSHeader implements PESegment {
	public static final int MZ_MAGIC = 0x5A4D;
	/*
typedef struct _IMAE_DOS_HEADER {		//DOS .EXE header                                    Offset
		WORD e_magic;						//Magic number;                                      0x00
		WORD e_cblp;                        //Bytes on last page of file                         0x02
		WORD e_cp;                          //Pages in file										 0x04
		WORD e_crlc;                        //Relocations                                        0x06
		WORD e_cparhdr;						//Size of header in paragraphs                       0x08
		WORD e_minalloc;                    //Minimum extra paragraphs needed                    0x0A
		WORD e_maxalloc;					//Maximum extra paragraphs needed                    0x0C
		WORD e_ss;                          //Initial (relative) SS value						 0x0E
		WORD e_sp;							//Initial SP value									 0x10
		WORD e_csum;						//Checksum											 0x12
		WORD e_ip;							//Initial IP value									 0x14
		WORD e_cs;							//Initial (relative) CS value                        0x16
		WORD e_lfarlc;						//File address of relocation table                   0x18
		WORD e_ovno;						//Overlay number                                     0x1A
		WORD e_res[4];						//Reserved words                                     0x1C
		WORD e_oemid;						//OEM identifier (for e_oeminfo)                     0x24
		WORD e_oeminfo;						//OEM information; e_oemid specific                  0x26
		WORD e_res2[10];					//Reserved words                                     0x28
		LONG e_lfanew;						//File address of new exe header                     0x3C
	};
	*/
	public char cblp;
	public char pages;
	public char relocations;
	public char parHeaderSize;


	@Override
	public void toByteArray(PEFile owner, ByteList w) {

	}

	@Override
	public void fromByteArray(PEFile owner, ByteList r) {

	}
}
