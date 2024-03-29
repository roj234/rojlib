package roj.exe.elf;

import roj.collect.SimpleList;
import roj.exe.ExeFile;
import roj.io.source.Source;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/30 17:30
 */
public class ElfFile extends ExeFile {
	ElfHeader elfHeader;
	SimpleList<ElfSectionE> programs = new SimpleList<>();
	SimpleList<ElfSectionD> datas = new SimpleList<>();

	public ElfFile(Source src) throws IOException {
		super(src);
		this.elfHeader = new ElfHeader();
		if (src.length() > 0) {
			read();
		} else {
			//this.headerOff = -1;
		}
	}

	static final long MAGIC = 0x7f454c4601010100L;

	@Override
	public void read() throws IOException {
		read(0, 8);
		if (rb.readLong() != MAGIC || rb.readLong() != 0) throw new IOException("Wrong magic word");

		read(-1, 44);
		elfHeader.fromByteArray(this, rb);

		programs.clear();
		programs.ensureCapacity(elfHeader.programCount);
		read(elfHeader.programOffset, elfHeader.programCount * ElfSectionE.SIZE);
		for (int i = elfHeader.programCount - 1; i >= 0; i--) {
			ElfSectionE e = new ElfSectionE();
			e.fromByteArray(this, rb);
			programs.add(e);
		}

		datas.clear();
		datas.ensureCapacity(elfHeader.sectionCount);
		read(elfHeader.sectionOffset, elfHeader.sectionCount * ElfSectionD.SIZE);
		for (int i = elfHeader.sectionCount - 1; i >= 0; i--) {
			ElfSectionD e = new ElfSectionD();
			e.fromByteArray(this, rb);
			datas.add(e);
		}
	}
}
