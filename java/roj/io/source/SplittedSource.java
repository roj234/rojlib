package roj.io.source;

import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.Files;

/**
 * @author Roj234
 * @since 2023/3/13 0013 5:11
 */
public class SplittedSource extends Source {
	private final File path;
	private final String name;

	private int sid;
	private Source s;

	private long offset;
	private final long fragmentSize;

	private final SimpleList<Object> ref;
	private final Object[] cache;

	private SplittedSource(Source[] sources) {
		path = null;
		name = null;
		cache = null;

		fragmentSize = -1;

		ref = SimpleList.asModifiableList((Object[]) sources);
		s = sources[0];
	}
	private SplittedSource(File file, long fragSize) throws IOException {
		path = file.getParentFile();
		name = file.getName();
		cache = new Object[16];

		ref = new SimpleList<>();

		for (int i = 1; i <= 999; i++) {
			t.clear();
			t.append(name).append('.').padNumber(i, 3);

			File splitFile = new File(path, t.toString());
			if (!splitFile.isFile()) break;

			ref.add(splitFile);
		}

		for (int i = 0; i < ref.size()-1; i++) {
			File f = (File) ref.get(i);
			long len = f.length();
			if (fragSize != len) {
				if (fragSize > 0) {
					fragSize = -1;
					break;
				} else {
					fragSize = len;
				}
			}
		}

		fragmentSize = fragSize;

		// create new
		if (ref.isEmpty()) {
			sid = -1;
			next();
		} else {
			s = getSource(0);
		}
	}

	public SplittedSource(FileSource src, long size) throws IOException {
		this(new File(src.getFile().getParentFile(), IOUtil.fileName(src.getFile().getName())), size);
	}

	public static SplittedSource fixedSize(File file, long size) throws IOException { return new SplittedSource(file, size); }
	public static SplittedSource dynSize(File file) throws IOException { return new SplittedSource(file, -1); }
	public static SplittedSource concated(Source... files) { return new SplittedSource(files); }

	public int read(byte[] b, int off, int len) throws IOException {
		if (len <= 0) return 0;

		int read = 0;
		while (read < len) {
			int r = s.read(b, off+read, len-read);
			if (r < len) {
				if (sid +1 == ref.size()) break;
				next();
			} else {
				read += r;
			}
		}

		return read == 0 ? -1 : read;
	}

	public void write(byte[] b, int off, int len) throws IOException { write(IOUtil.SharedCoder.get().wrap(b, off, len)); }
	public void write(DynByteBuf data) throws IOException {
		if (fragmentSize <= 0) throw new NonWritableChannelException();

		int readable = data.readableBytes();
		written += readable;

		while (readable > 0) {
			int writable = (int) Math.min(Integer.MAX_VALUE, fragmentSize - s.position());

			if (readable <= writable) {
				s.write(data);
				return;
			}

			s.write(data.slice(writable));
			readable -= writable;
			next();
		}
	}

	private final CharList t = new CharList();
	private void next() throws IOException {
		if (sid < ref.size()-1) {
			s = getSource(++sid);
			s.seek(0);
			return;
		}

		ensureFileSource();
		if (ref.size() >= 999) throw new IOException("文件分卷过多：999");

		t.clear();
		t.append(name).append('.').padNumber(ref.size()+1, 3);
		File file = new File(path, t.toString());
		if (!file.isFile()) {
			try {
				IOUtil.allocSparseFile(file, fragmentSize);
			} catch (IOException ignored) {}
		}
		ref.add(file);
		s = getSource(++sid);
	}

	private Source getSource(int sid) throws IOException {
		Object o = ref.get(sid);
		if (o instanceof Source) return (Source) o;

		int minId = 0, minUsage = 0xFFFF;
		for (int i = 0; i < cache.length; i++) {
			MutableInt id = (MutableInt) cache[i++];
			if (id == null) break;

			int usage = id.value & 0xFFFF;
			if ((id.value>>>16) == sid) {
				if (usage < 0xFFFF) id.value++;
				return (Source) cache[i];
			}

			if (usage <= minUsage) {
				minId = i-1;
				minUsage = usage;
			}
		}

		MutableInt id = (MutableInt) cache[minId];
		if (id == null) cache[minId] = id = new MutableInt();
		id.setValue(sid << 16);

		Source v = (Source) cache[minId+1];
		if (v != null) v.close();
		cache[minId+1] = v = new FileSource((File) o);

		return v;
	}
	private long getLength(int i) throws IOException {
		Object o = ref.get(i);
		if (o instanceof Source) return ((Source) o).length();
		return ((File) o).length();
	}

	public void seek(long pos) throws IOException {
		if (fragmentSize > 0) {
			trimLastFile();

			s = getSource(sid = (int) (pos / fragmentSize));
			s.seek(pos % fragmentSize);
		} else {
			offset = pos;

			for (int i = 0; i < ref.size(); i++) {
				long len = getLength(i);
				if (pos >= len) {
					pos -= len;
				} else {
					offset -= pos;
					s = getSource(sid = i);
					s.seek(pos);
					return;
				}
			}
		}
	}
	public long position() throws IOException { return (fragmentSize > 0 ? sid * fragmentSize : offset) + s.position(); }

	public void setLength(long length) throws IOException {
		ensureFileSource();

		int newFrags = (int) (length/fragmentSize) + 1;

		for (int i = 0; i < cache.length; i++) {
			MutableInt id = (MutableInt) cache[i++];
			if (id == null) break;

			if ((id.value>>>16) >= newFrags) {
				((Source) cache[i]).close();
				cache[i] = null;
			}
		}

		for (int i = ref.size()-1; i >= newFrags; i--)
			Files.deleteIfExists(((File) ref.remove(i)).toPath());

		getSource(newFrags-1).setLength(length%fragmentSize);
	}
	public long length() throws IOException {
		if (fragmentSize > 0) return (ref.size()-1)*fragmentSize + getLength(ref.size()-1);

		long len = 0;
		for (int i = 0; i < ref.size(); i++) len += getLength(i);
		return len;
	}

	public void close() throws IOException {
		Throwable e1 = null;
		if (cache == null) {
			for (int i = 0; i < ref.size(); i++) {
				try {
					((Source) ref.get(i)).close();
				} catch (Throwable e) { e1 = e; }
			}
		} else {
			try {
				trimLastFile();
			} catch (Throwable e) { e1 = e; }

			for (int i = 0; i < cache.length; i++) {
				MutableInt id = (MutableInt) cache[i++];
				if (id == null) break;

				try {
					((Source) cache[i]).close();
				} catch (Throwable e) { e1 = e; }
				cache[i] = null;
			}
		}
		ref.clear();

		if (e1 != null) Helpers.athrow(e1);
	}

	private void trimLastFile() throws IOException {
		if (fragmentSize > 0 && sid == ref.size()-1) s.setLength(s.position());
	}

	public Source threadSafeCopy() throws IOException {
		ensureFileSource();
		return new ReadonlySource(new SplittedSource(new File(path, name), fragmentSize));
	}

	public void moveSelf(long from, long to, long length) {
		throw new UnsupportedOperationException("咳咳，抱歉，懒得做了");
	}

	private void ensureFileSource() throws IOException {
		if (path == null) throw new IOException("无法扩展通过concated函数创建的SplittedSource");
	}
}