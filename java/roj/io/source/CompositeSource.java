package roj.io.source;

import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @author Roj234
 * @since 2023/3/13 5:11
 */
public final class CompositeSource extends Source {
	private final File path;
	private final String name;

	private int sid;
	private Source s;

	private long offset = -1;
	private final long fragmentSize;
	private boolean writable;

	private final SimpleList<Object> ref;

	private CompositeSource(Source[] sources) {
		path = null;
		name = null;

		fragmentSize = -1;

		ref = SimpleList.asModifiableList((Object[]) sources);
		s = sources[0];
	}
	private CompositeSource(File file, long fragSize, boolean writable) throws IOException {
		path = file.getAbsoluteFile().getParentFile();
		name = file.getName();

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
				} else if (fragSize == 0) {
					fragSize = len;
				}
			}
		}

		fragmentSize = fragSize;
		this.writable = writable;

		// create new
		sid = -1;
		if (ref.isEmpty()) {
			next();
		} else {
			s = setSource(0);
		}
	}

	public static CompositeSource fixed(File file, long size) throws IOException { return new CompositeSource(file, size, true); }
	public static CompositeSource dynamic(File file, boolean writable) throws IOException { return new CompositeSource(file, -1, writable); }
	public static CompositeSource concat(Source... files) { return new CompositeSource(files); }

	public int read(byte[] b, int off, int len) throws IOException {
		if (len <= 0) return 0;

		int read = 0;
		while (read < len) {
			int r = s.read(b, off+read, len-read);
			if (r < len) {
				if (sid+1 == ref.size()) break;
				next();
			} else {
				read += r;
			}
		}

		return read == 0 ? -1 : read;
	}

	public void write(byte[] b, int off, int len) throws IOException { write(IOUtil.SharedBuf.get().wrap(b, off, len)); }
	public void write(DynByteBuf data) throws IOException {
		if (!writable) throw new IOException("源是只读的");

		int readable = data.readableBytes();
		written += readable;

		if (fragmentSize <= 0) {
			s.write(data);
			return;
		}

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

	@Override
	public void put(Source src, long off, long len) throws IOException {
		if (!writable) throw new IOException("源是只读的");

		written += len;

		if (fragmentSize <= 0) {
			s.put(src, off, len);
			return;
		}

		while (len > 0) {
			int writable = (int) Math.min(Integer.MAX_VALUE, fragmentSize - s.position());

			if (len <= writable) {
				s.put(src, off, len);
				return;
			}

			s.put(src, off, writable);
			off += writable;
			len -= writable;
			next();
		}
	}

	private final CharList t = new CharList();

	public void setSourceId(int sid) throws IOException {
		s = setSource(sid);
		s.seek(0);
	}
	public Source getSource() {return s;}
	public int ordinal() {return sid;}

	public void next() throws IOException {
		offset = -1;

		if (sid < ref.size()-1) {
			s = setSource(1+sid);
			s.seek(0);
			return;
		}

		ensureFileSource();
		if (ref.size() >= 999) throw new IOException("文件分卷过多：999");

		t.clear();
		t.append(name).append('.').padNumber(ref.size()+1, 3);
		File file = new File(path, t.toString());
		if (!file.isFile() && fragmentSize > 0) {
			try {
				IOUtil.createSparseFile(file, fragmentSize);
			} catch (IOException ignored) {}
		}
		ref.add(file);
		s = setSource(1+sid);
	}

	private Source setSource(int sid) throws IOException {
		if (this.sid == sid) return s;
		this.sid = sid;

		if (s != null) s.close();

		return getSource(sid);
	}
	private Source getSource(int sid) throws IOException {
		Object o = ref.get(sid);
		if (o instanceof Source) return (Source) o;
		return new FileSource((File) o, writable);
	}
	private long getLength(int i) throws IOException {
		Object o = ref.get(i);
		if (o instanceof Source) return ((Source) o).length();
		return ((File) o).length();
	}
	private long getOffset() throws IOException {
		if (offset >= 0) return offset;

		long off = 0;
		for (int i = 0; i < sid; i++) {
			Object o = ref.get(i);
			off += o instanceof File f ? f.length() : ((Source)o).length();
		}
		return offset = off;
	}

	public void seek(long pos) throws IOException {
		if (fragmentSize > 0) {
			s = setSource((int) (pos / fragmentSize));
			s.seek(pos % fragmentSize);
		} else {
			offset = -1;

			for (int i = 0; i < ref.size(); i++) {
				long len = getLength(i);
				if (pos > len) {
					pos -= len;
				} else {
					s = setSource(i);
					s.seek(pos);
					return;
				}
			}
		}
	}
	public long position() throws IOException { return (fragmentSize > 0 ? sid * fragmentSize : getOffset()) + s.position(); }

	public void setLength(long length) throws IOException {
		ensureFileSource();
		if (!writable) throw new IOException("源是只读的");

		if (fragmentSize <= 0) {
			offset = -1;

			long off = 0;
			int i = 0;
			while (i < ref.size()) {
				long fLen = ((File) ref.get(i++)).length();
				if (off+fLen > length) break;
				off += fLen;
			}

			for (int j = ref.size()-1; j >= i; j--) {
				Files.deleteIfExists(((File) ref.remove(j)).toPath());
			}

			try (Source source = getSource(i-1)) {
				source.setLength(length - off);
			}
			return;
		}

		int newFrags = (int) (length/fragmentSize) + 1;

		for (int i = ref.size()-1; i >= newFrags; i--)
			Files.deleteIfExists(((File) ref.remove(i)).toPath());

		try (Source source = getSource(newFrags-1)) {
			source.setLength(length%fragmentSize);
		}
	}
	public long length() throws IOException {
		if (fragmentSize > 0) return (ref.size()-1)*fragmentSize + getLength(ref.size()-1);

		long len = 0;
		for (int i = 0; i < ref.size(); i++) len += getLength(i);
		return len;
	}

	@Override
	public boolean isWritable() {return writable;}

	public void close() throws IOException {
		super.close();
		Throwable e1 = null;
		if (path == null) {
			for (int i = 0; i < ref.size(); i++) {
				try {
					((Source) ref.get(i)).close();
				} catch (Throwable e) { e1 = e; }
			}
		} else {
			s.close();
		}
		ref.clear();

		if (e1 != null) Helpers.athrow(e1);
	}

	public Source threadSafeCopy() throws IOException {
		ensureFileSource();
		return new CompositeSource(new File(path, name), fragmentSize, false);
	}

	public void moveSelf(long from, long to, long length) {
		throw new UnsupportedOperationException("咳咳，抱歉，懒得做了");
	}

	private void ensureFileSource() throws IOException {
		if (path == null) throw new IOException("无法扩展通过concated函数创建的FragmentSource");
	}
}