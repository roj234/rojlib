package roj.io.source;

import roj.collect.SimpleList;
import roj.io.FileUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

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

	private final SimpleList<Source> ref;

	private SplittedSource(Source[] sources, long fragSize) {
		path = null;
		name = null;

		fragmentSizeL = fragSize;
		fragmentSize = (int) Math.min(Integer.MAX_VALUE, fragSize);

		ref = new SimpleList<>(sources);
		s = sources[0];
	}
	private SplittedSource(File file, long fragSize) throws IOException {
		path = file.getParentFile();
		name = file.getName();

		fragmentSizeL = fragSize;
		fragmentSize = (int) Math.min(Integer.MAX_VALUE, fragSize);

		ref = new SimpleList<>();
		frag = -1;
		next();
	}

	public SplittedSource(FileSource src, int size) throws IOException {
		path = src.getSource().getParentFile();
		String name = src.getSource().getName();
		if (!name.endsWith(".001")) throw new ZipException("文件的扩展名必须为001");
		this.name = FileUtil.noExtName(name);

		fragmentSizeL = size;
		fragmentSize = (int) Math.min(Integer.MAX_VALUE, size);

		ref = new SimpleList<>();
		ref.add(s = src);
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

	private void next() throws IOException {
		if (ref.size() > frag + 1) {
			s = ref.get(++frag);
			s.seek(0);
			return;
		}

		ensureFileSource();
		if (s != null && s.length() != fragmentSizeL) s.setLength(fragmentSizeL);
		if (ref.size() >= 999) throw new IOException("文件分卷过多：999");

		CharList sb = new CharList().append(name).append('.');
		TextUtil.pad(sb, ref.size() + 1, 3);
		s = new FileSource(new File(path, sb.toString()));
		ref.add(s);
		frag++;
	}

	protected void getNextSource() {}

	private final ByteList wrapper = new ByteList();
	public void write(byte[] b, int off, int len) throws IOException {
		wrapper.list = b;
		wrapper.rIndex = off;
		wrapper.wIndex(off + len);
		write(wrapper);
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
		s = ref.get(frag = (int) (pos / fragmentSizeL));
		s.seek(pos % fragmentSizeL);
	}
	public long position() throws IOException {
		return frag * fragmentSizeL + s.position();
	}

	public void setLength(long length) throws IOException {
		ensureFileSource();

		int frags = (int) (length/fragmentSizeL) + 1;
		for (int i = ref.size()-1; i >= frags; i--) {
			FileSource s = (FileSource) ref.remove(i);

			s.close();
			s.getSource().delete();
		}
	}
	public long length() throws IOException {
		return (ref.size()-1)*fragmentSizeL + ref.get(ref.size()-1).length();
	}

	public DataInput asDataInput() { noImpl(); return null; }
	public InputStream asInputStream() { noImpl(); return null; }

	public void close() throws IOException {
		Throwable e1 = null;
		for (int i = 0; i < ref.size(); i++) {
			try {
				Source src = ref.get(i);
				if (path != null && i != ref.size() - 1) src.setLength(fragmentSizeL);
				src.close();
			} catch (Throwable e) {
				e1 = e;
			}
		}
		ref.clear();

		if (e1 != null) Helpers.athrow(e1);
	}

	public Source threadSafeCopy() {
		throw new UnsupportedOperationException("咳咳，抱歉，懒得做了");
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
