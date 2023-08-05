package roj.io.source;

import roj.collect.LFUCache;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/3/13 0013 5:11
 */
public class SplittedSource extends Source {
	private final File path;
	private final String name;
	private Source s;

	private int frag;
	private final int fragmentSize;
	private final long fragmentSizeL;

	private final SimpleList<Object> ref;
	private final LFUCache<Integer,FileSource> cache;

	private SplittedSource(Source[] sources, long fragSize) {
		path = null;
		name = null;
		cache = null;

		fragmentSizeL = fragSize;
		fragmentSize = (int) Math.min(Integer.MAX_VALUE, fragSize);

		ref = SimpleList.asModifiableList((Object[]) sources);
		s = sources[0];
	}
	private SplittedSource(File file, long fragSize) throws IOException {
		path = file.getParentFile();
		name = file.getName();
		cache = new LFUCache<>(4, 1);
		cache.setEvictListener((no, source) -> {
			try { source.close(); } catch (IOException ignored) {}
		});

		fragmentSizeL = fragSize;
		fragmentSize = (int) Math.min(Integer.MAX_VALUE, fragSize);

		ref = new SimpleList<>();

		for (int i = 0; i < 999; i++) {
			t.clear();
			t.append(name).append('.').padNumber(ref.size()+1, 3);

			File splitFile = new File(path, t.toString());
			if (!splitFile.isFile()) break;

			ref.add(splitFile);
		}

		// create new
		if (ref.isEmpty()) {
			frag = -1;
			next();
		} else {
			s = getSource(0);
		}
	}

	public SplittedSource(FileSource src, long size) throws IOException {
		this(new File(src.getSource().getParentFile(), IOUtil.noExtName(src.getSource().getName())), size);
	}

	public static SplittedSource fixedSize(File file, long size) throws IOException {
		return new SplittedSource(file, size);
	}
	public static SplittedSource concated(Source... files) throws IOException {
		long fragmentSize = files[0].length();
		for (int i = 1; i < files.length-1; i++) {
			Source file = files[i];
			if (file.length() != fragmentSize) throw new IOException("分片大小必须相同");
		}
		return new SplittedSource(files, fragmentSize);
	}

	public int read(byte[] b, int off, int len) throws IOException {
		if (len <= 0) return 0;

		int avl = (int) Math.min(Integer.MAX_VALUE, fragmentSizeL - s.position());

		int r = s.read(b, off, Math.min(avl, len));
		if (r < avl) return r;
		if (len == avl) return r;

		off += avl;
		len -= avl;
		next();

		while (len >= fragmentSize) {
			int r1 = s.read(b, off, fragmentSize);
			if (r1 > 0) r += r1;
			if (r1 < fragmentSize) return r;

			off += fragmentSize;
			len -= fragmentSize;
			next();
		}

		if (len > 0) {
			int r1 = s.read(b, off, len);
			if (r1 > 0) r += r1;
			written = len;
		}

		return r;
	}

	private final CharList t = new CharList();
	private void next() throws IOException {
		if (ref.size() > frag + 1) {
			s = getSource(++frag);
			s.seek(0);
			return;
		}

		ensureFileSource();
		if (s != null && s.length() != fragmentSizeL) s.setLength(fragmentSizeL);
		if (ref.size() >= 999) throw new IOException("文件分卷过多：999");

		t.clear();
		t.append(name).append('.').padNumber(ref.size()+1, 3);
		File file = new File(path, t.toString());
		if (!file.isFile()) {
			try {
				IOUtil.allocSparseFile(file, fragmentSizeL);
			} catch (IOException ignored) {}
		}
		ref.add(file);
		s = getSource(ref.size()-1);
		frag++;
	}

	private Source getSource(int i) throws IOException {
		if (cache == null) return (Source) ref.get(i);

		FileSource s = cache.get(i);
		if (s == null) cache.put(i, s = new FileSource((File) ref.get(i)));
		return s;
	}

	public void write(byte[] b, int off, int len) throws IOException {
		write(IOUtil.SharedCoder.get().wrap(b, off, len));
	}
	public void write(DynByteBuf data) throws IOException {
		int remain = data.readableBytes();
		written += remain;

		int avl = (int) Math.min(Integer.MAX_VALUE, fragmentSizeL - s.position());

		if (remain <= avl) {
			s.write(data);
			return;
		}

		data.wIndex(data.wIndex() - remain + avl);
		remain -= avl;

		s.write(data);
		next();

		while (remain >= fragmentSize) {
			data.wIndex(data.wIndex() + fragmentSize);
			remain -= fragmentSize;

			s.write(data);
			next();
		}

		if (remain > 0) {
			data.wIndex(data.wIndex() + remain);
			s.write(data);
		}
	}

	public void seek(long pos) throws IOException {
		s = getSource(frag = (int) (pos / fragmentSizeL));
		s.seek(pos % fragmentSizeL);
	}
	public long position() throws IOException {
		return frag * fragmentSizeL + s.position();
	}

	public void setLength(long length) throws IOException {
		ensureFileSource();

		int frags = (int) (length/fragmentSizeL) + 1;
		for (int i = ref.size()-1; i >= frags; i--) {
			Source s = cache.remove(i);
			if (s != null) {
				s.setLength(0);
				s.close();
			}

			((File)ref.remove(i)).delete();
		}

		getSource(frags-1).setLength(length%fragmentSizeL);
	}
	public long length() throws IOException {
		return (ref.size()-1)*fragmentSizeL + getSource(ref.size()-1).length();
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
			for (int i = 0; i < ref.size(); i++) {
				try {
					File src = (File) ref.get(i);
					if (i != ref.size()-1 && src.length() != fragmentSizeL) getSource(i).setLength(fragmentSizeL);
				} catch (Throwable e) { e1 = e; }
			}
			for (Map.Entry<Integer, FileSource> entry : cache.entrySet()) {
				try {
					entry.getValue().close();
				} catch (Throwable e) { e1 = e; }
			}
			cache.clear();
		}
		ref.clear();

		if (e1 != null) Helpers.athrow(e1);
	}

	public Source threadSafeCopy() throws IOException {
		ensureFileSource();
		return new ReadonlySource(new SplittedSource(new File(path, name), fragmentSizeL));
	}

	public void moveSelf(long from, long to, long length) {
		throw new UnsupportedOperationException("咳咳，抱歉，懒得做了");
	}

	private static void noImpl() {
		throw new UnsupportedOperationException("Wrap me use BufferedSource!");
	}
	private void ensureFileSource() throws IOException {
		if (path == null) throw new IOException("无法扩展通过concated函数创建的SplittedSource");
	}
}
