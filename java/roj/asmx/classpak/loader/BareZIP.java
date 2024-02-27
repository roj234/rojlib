package roj.asmx.classpak.loader;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

final class BareZIP {
	public static int getMin2PowerOf(int cap) {
		int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
		return (n < 0) ? 1 : (n >= 1073741824) ? 1073741824 : n + 1;
	}

	public final File file;
	SDI r;

	private final ZFILE[] files;
	private final int mask;

	public BareZIP(File file) throws IOException {
		this.file = file;
		this.r = new SDI(file);
		try {
			long off = Math.max(r.in.length()-512, 0);

			MappedByteBuffer mb = r.in.getChannel().map(MapMode.READ_ONLY, off, r.in.length()-off);
			mb.order(ByteOrder.BIG_ENDIAN);

			int pos = mb.capacity()-3;
			while (pos > 0) {
				if ((mb.get(--pos) & 0xFF) != 'P') continue;

				int field = mb.getInt(pos);
				if (field == 0x504b0506) break;
			}
			// have no way to unload this;

			r.seek(off+pos+10);

			int size = r.readUShortLE();
			size = getMin2PowerOf(size);
			files = new ZFILE[size];
			mask = size-1;
			r.skip(4);
			int pos1 = r.readIntLE();
			r.seek(pos1);
			while (r.readInt() == 0x504b0102) readCEN(r);
		} catch (Throwable e) {
			r.close();
			throw e;
		}
	}

	private void readCEN(SDI r) throws IOException {
		ZFILE v = new ZFILE();

		r.skip(4);

		int flags = r.readUShortLE();
		int method = r.readUShortLE();

		if ((((flags&1)|method) & ~8) != 0) v.flag = 1;
		else v.flag = (byte) method;

		r.skip(8);
		v.len = r.readIntLE(); //cSize
		r.skip(4);
		int nameLen = r.readUShortLE();
		int toSkip = r.readUShortLE()+r.readUShortLE();
		r.skip(8);
		long locPos = r.readIntLE()&0xFFFFFFFFL;

		v.name = r.readUTF(nameLen);
		r.skip(toSkip);

		long off = r.in.getFilePointer();

		r.in.seek(locPos + 28);
		toSkip = r.in.read() | (r.in.read() << 8);
		assert toSkip >= 0;

		v.pos = (int) (locPos + 30 + nameLen + toSkip);

		r.in.seek(off);

		if (!v.name.endsWith("/")) {
			int i = v.name.hashCode()&mask;
			v.next = files[i];
			files[i] = v;
		}
	}

	public final ZFILE get(String name) {
		if (files != null) {
			ZFILE obj = files[name.hashCode() & mask];
			while (obj != null) {
				if (name.equals(obj.name)) return obj;
				obj = obj.next;
			}
		}
		return null;
	}

	public InputStream getStream(ZFILE entry) throws IOException {
		SDI sdi;
		synchronized (this) {
			sdi = r;
			if (sdi == null) {
				sdi = new SDI(file);
			} else {
				r = null;
			}
		}
		sdi.seek(entry.pos);

		if (entry.flag != 0) {
			if (entry.flag == 1) throw new IOException("flag error:".concat(entry.name));
			Inflater inf = new Inflater(true);
			return new INF(sdi, inf);
		}

		return new FilterInputStream(sdi) {
			int rd = entry.len;
			@Override
			public int read() throws IOException {
				if (rd <= 0) return -1;
				rd--;
				return in.read();
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				if (len <= 0) return 0;
				if (rd <= 0) return -1;
				int min = Math.min(rd, len);
				rd -= min;
				return in.read(b, off, min);
			}

			@Override
			public void close() throws IOException {
				synchronized (BareZIP.this) {
					if (rd < 0) return;
					rd = -1;

					if (r == null) r = (SDI) in;
					else super.close();
				}
			}
		};
	}

	private static final class INF extends InflaterInputStream {
		public INF(SDI sdi, Inflater inf) { super(sdi, inf, 512); }
		@Override
		public void close() throws IOException { inf.end(); super.close(); }
	}

	static final class ZFILE {
		ZFILE() {}

		byte flag;
		String name;
		int pos, len;

		ZFILE next;
	}
}