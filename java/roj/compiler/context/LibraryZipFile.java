package roj.compiler.context;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashSet;
import roj.crypt.CRC32s;
import roj.io.IOUtil;
import roj.text.Interner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:52
 */
public class LibraryZipFile implements Library {
	public final ZipFile zf;
	private final MyHashSet<String> info;
	private final String moduleName;

	public LibraryZipFile(File file) throws IOException {
		this.zf = new ZipFile(file, ZipFile.FLAG_BACKWARD_READ);
		this.info = new MyHashSet<>();

		for (ZEntry entry : zf.entries()) {
			String name = entry.getName();
			if (name.endsWith(".class")) info.add(Interner.intern(name.substring(0, name.length()-6)));
		}

		moduleName = Library.super.moduleName();
	}

	@Override public String versionCode() {
		int hash = CRC32s.INIT_CRC;
		var b = IOUtil.getSharedByteBuf();

		for (ZEntry entry : zf.entries()) {
			b.putChars(entry.getName()).putShort(0).putInt(entry.getCrc32()).putLong(entry.getModificationTime());
			if (b.wIndex() > 1024) {
				hash = CRC32s.update(hash, b.list, 0, b.wIndex());
				b.clear();
			}
		}

		return "archive@"+zf.source()+"-"+CRC32s.retVal(hash);
	}
	@Override public String moduleName() {return moduleName;}
	@Override public Collection<String> content() {return info;}

	@Override
	public ConstantData get(CharSequence name) {
		try (var in = zf.getStream(name.toString().concat(".class"))) {
			return in == null ? null : Parser.parseConstants(IOUtil.read(in));
		} catch (IOException e) {
			return null;
		}
	}

	@Override public InputStream getResource(CharSequence name) throws IOException {return zf.getStream(name.toString());}
	@Override public void close() throws Exception { zf.close(); }
}