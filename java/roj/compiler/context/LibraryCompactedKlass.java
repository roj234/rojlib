package roj.compiler.context;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.ClassNode;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.io.MyDataInput;
import roj.io.MyDataInputStream;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/10/10 0010 1:01
 */
public final class LibraryCompactedKlass implements Library {
	public static List<Library> loadCache(File symTable) throws IOException {
		List<Library> output = new SimpleList<>();
		var zf = new ZipFile(symTable);

		for (ZEntry entry : zf.entries()) {
			if (!entry.getName().endsWith("/v")) {
				try (var in = MyDataInputStream.wrap(zf.getStream(entry))) {
					output.add(new LibraryCompactedKlass(in, zf, entry.getName()));
				} catch (Exception e) {
					IOUtil.closeSilently(zf);
					throw e;
				}
			}
		}

		return output;
	}
	public static void saveCache(File symCache, List<LibraryCompactedKlass> caches) throws IOException {
		try (var tmp = new ZipFileWriter(symCache);
			 var ob = new ByteList.ToStream(tmp)) {
			for (var cache : caches) {
				tmp.beginEntry(new ZEntry(cache.module));
				cache.serializeK(ob);
				ob.flush();

				tmp.beginEntry(new ZEntry(cache.module+"/v"));
				cache.serializeV(ob);
				ob.flush();
			}

		}
	}

	private final String module;
	private final ToIntMap<String> table = new ToIntMap<>();
	private SimpleList<ConstantPool> pool;
	private DynByteBuf data;

	public LibraryCompactedKlass(String module) {
		this.module = module;
		this.pool = SimpleList.asModifiableList(new ConstantPool());
		this.data = new ByteList();
	}

	public void add(ClassNode data) {
		//Library.removeUnrelatedAttribute(data);

		if (pool.getLast().array().size() + data.cp.array().size() >= 0xFFFF) {
			pool.add(new ConstantPool());
		}
		table.putInt(data.name(), this.data.wIndex() | (pool.size()-1) << 24);

		data.parsed();
		data.cp = null;
		data.getBytesNoCp(this.data.putInt(data.version), pool.getLast());
	}

	public void serializeK(DynByteBuf buf) {
		buf.putAscii("LAVASYM4").putVUIUTF(module).putVUInt(table.size());
		for (var entry : table.selfEntrySet()) {
			buf.putVUIUTF(entry.k).putVUInt(entry.v);
		}
	}
	public void serializeV(DynByteBuf buf) {
		buf.putVUInt(pool.size());
		for (int i = 0; i < pool.size(); i++) {
			pool.get(i).write(buf, false);
		}
		buf.put(data);
	}

	public LibraryCompactedKlass(MyDataInput buf, ZipFile zf, String module) throws IOException {
		if (!buf.readAscii(8).equals("LAVASYM4")) throw new IllegalStateException("Magic number error");
		this.module = module;

		var len = buf.readVUInt();
		table.ensureCapacity(len);
		for (int i = 0; i < len; i++) {
			table.putInt(buf.readVUIUTF(), buf.readVUInt());
		}

		this.zf = zf;
		this.ze = zf.getEntry(module+"/v");
	}

	private ZipFile zf;
	private ZEntry ze;
	private void readV() {
		try {
			var buf = zf.get(ze, IOUtil.getSharedByteBuf());

			var len = buf.readVUInt();

			pool = new SimpleList<>(len);
			for (int i = 0; i < len; i++) {
				var p = new ConstantPool();
				p.read(buf, ConstantPool.CHAR_STRING);
				pool.add(p);
			}

			data = new ByteList(buf.toByteArray());
		} catch (Exception e) {
			throw new IllegalStateException("无法读取ValueCache", e);
		}
		zf = null;
		ze = null;

	}

	@Override public String moduleName() {return module;}
	@Override public Collection<String> content() {return table.keySet();}
	@Override
	public ClassNode get(CharSequence name) {
		int offset = table.getOrDefault(name, -1);
		if (offset < 0) return null;

		if (pool == null) readV();
		var cp = pool.get(offset >>> 24);
		offset &= 0xFFFFFF;

		var slice = data.slice(offset, data.wIndex() - offset);
		return Parser.parseConstantsNoCp(slice, cp, slice.readInt());
	}
}