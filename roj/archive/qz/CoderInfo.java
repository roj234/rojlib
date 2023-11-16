package roj.archive.qz;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/5/30 0030 17:50
 */
final class CoderInfo {
	private final QZCoder self;
	final Object[] uses;
	final int provides;
	/**
	 * 同时也是output stream的索引
	 */
	private int sizeId;

	CoderInfo(QZCoder c, int sizeId) {
		self = c;
		if (c instanceof QZComplexCoder) {
			uses = new Object[((QZComplexCoder) c).useCount()];
			provides = ((QZComplexCoder) c).provideCount();
		} else {
			uses = new Object[1];
			provides = 1;
		}
		this.sizeId = sizeId;
	}

	@Override
	public String toString() { return String.valueOf(sizeId)+'#'+self; }

	InputStream[] getInputStream(long[] outSizes, InputStream[] fs, Map<CoderInfo, InputStream[]> buffer, byte[] pass, int memory) throws IOException {
		InputStream[] assoc = new InputStream[uses.length];
		for (int i = 0; i < uses.length; i++) {
			Object o = uses[i];
			if (o.getClass() == Integer.class) {
				assoc[i] = fs[(int) o];
			} else if (o.getClass() == CoderInfo.class) {
				InputStream[] arr = buffer.get(o);
				if (arr == null) {
					arr = ((CoderInfo) o).getInputStream(outSizes, fs, buffer, pass, memory);
					buffer.put((CoderInfo) o, arr);
				}
				assoc[i] = arr[0];
			} else {
				IntMap.Entry<CoderInfo> entry = Helpers.cast(o);
				InputStream[] arr = buffer.get(entry.getValue());
				if (arr == null) {
					arr = entry.getValue().getInputStream(outSizes, fs, buffer, pass, memory);
					buffer.put(entry.getValue(), arr);
				}
				assoc[i] = arr[entry.getIntKey()];
			}
		}

		return self instanceof QZComplexCoder
			? ((QZComplexCoder) self).complexDecode(assoc, outSizes, sizeId)
			: new InputStream[] {self.decode(assoc[0], pass, outSizes[sizeId], memory)};
	}

	OutputStream getOutputStream(WordBlock b, OutputStream out) throws IOException {
		OutputStream[] streams = new OutputStream[b.extraSizes.length+1];
		// noinspection all
		streams[0] = new Chunk(b, out);
		for (int i = 1; i < streams.length; i++)
			// noinspection all
			streams[i] = new Chunk(b, i);

		return new Composer(getOutputStreams(b, streams, new MyHashMap<>())[0], streams);
	}
	static final class Chunk extends OutputStream {
		final WordBlock owner;
		final OutputStream os;

		private final DirectByteList oa;

		private final int offset;

		Chunk(WordBlock owner, int countOffset) {
			this.owner = owner;
			this.offset = countOffset-1;
			this.os = null;
			this.oa = DirectByteList.allocateDirect(1024);
		}

		Chunk(WordBlock owner, OutputStream out) {
			this.owner = owner;
			this.offset = -1;
			this.os = out;
			this.oa = null;
		}

		@Override
		public void write(int b) throws IOException {
			if (os != null) os.write(b);
			else oa.put(b);

			if (offset >= 0) owner.extraSizes[offset]++;
			else owner.size++;
		}

		@Override
		public void write(@Nonnull byte[] b, int off, int len) throws IOException {
			if (len <= 0) return;

			if (os != null) os.write(b, off, len);
			else oa.put(b, off, len);

			if (offset >= 0) owner.extraSizes[offset] += len;
			else owner.size += len;
		}

		void drain(OutputStream s) throws IOException {
			if (os == null) {
				byte[] b = ArrayCache.getByteArray(1024, false);
				try {
					while (true) {
						int len = Math.min(b.length, oa.readableBytes());
						if (len == 0) break;
						oa.read(b,0,len);
						s.write(b,0,len);
					}
				} finally {
					oa._free();
					ArrayCache.putArray(b);
				}
			}
		}
	}
	static final class Composer extends OutputStream implements Finishable {
		private final OutputStream out;
		OutputStream[] blockStream;
		public Composer(OutputStream out, OutputStream[] blockStream) {
			this.out = out;
			this.blockStream = blockStream;
		}

		public void write(int b) throws IOException { out.write(b); }
		public void write(@Nonnull byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
		public void flush() throws IOException { out.flush(); }
		public void finish() throws IOException { if (out instanceof Finishable) ((Finishable) out).finish(); }

		public void close() throws IOException {
			try {
				finish();
				flush();
			} catch (IOException ignored) {}
			try {
				out.close();
			} catch (IOException ignored) {}

			OutputStream ref = ((Chunk) blockStream[0]).os;
			for (int i = 1; i < blockStream.length; i++)
				((Chunk) blockStream[i]).drain(ref);
		}
	}

	private OutputStream[] getOutputStreams(WordBlock b, OutputStream[] fs, Map<CoderInfo, OutputStream[]> buffer) throws IOException {
		OutputStream[] assoc = new OutputStream[uses.length];
		for (int i = 0; i < uses.length; i++) {
			Object o = uses[i];
			if (o.getClass() == Integer.class) {
				assoc[i] = fs[(int) o];
			} else if (o.getClass() == CoderInfo.class) {
				OutputStream[] arr = buffer.get(o);
				if (arr == null) {
					arr = ((CoderInfo) o).getOutputStreams(b, fs, buffer);
					buffer.put((CoderInfo) o, arr);
				}
				assoc[i] = arr[0];
			} else {
				IntMap.Entry<CoderInfo> entry = Helpers.cast(o);
				OutputStream[] arr = buffer.get(entry.getValue());
				if (arr == null) {
					arr = entry.getValue().getOutputStreams(b, fs, buffer);
					buffer.put(entry.getValue(), arr);
				}
				assoc[i] = arr[entry.getIntKey()];
			}
		}

		OutputStream[] streams = self instanceof QZComplexCoder
			? ((QZComplexCoder) self).complexEncode(assoc) // actually provides
			: new OutputStream[] {self.encode(assoc[0])};

		if (sizeId >= 0) {
			for (int i = 0; i < streams.length; i++) {
				// noinspection all
				streams[i] = b.new Counter(streams[i], sizeId+i);
			}
		}
		return streams;
	}

	void pipe(int toStreamId, CoderInfo fromNode, int fromStreamId) {uses[toStreamId] = fromStreamId == 0 ? fromNode : new IntMap.Entry<>(fromStreamId, fromNode);}
	void setFileInput(int blockId, int streamId) {uses[streamId] = blockId;}

	void writeCoder(WordBlock b, ByteList buf) {
		ByteList w = IOUtil.getSharedByteBuf();

		CoderInfo[] sortedInfo = (CoderInfo[]) b.tmp;
		buf.putVUInt(sortedInfo.length);

		for (CoderInfo c : sortedInfo) {
			w.clear();
			c.self.writeOptions(w);

			byte[] id = c.self.id();

			int flags = id.length;
			if (c.self instanceof QZComplexCoder) flags |= 0x10;
			if (w.wIndex() > 0) flags |= 0x20;
			buf.put((byte) flags).put(id);

			if (c.self instanceof QZComplexCoder) {
				buf.putVUInt(c.uses.length).putVUInt(c.provides);
			}

			if (w.wIndex() > 0) buf.putVUInt(w.wIndex()).put(w);
		}

		// pipes and lengthMap
		int used = 0;
		int[] blocks = new int[b.extraSizes.length+1];

		for (CoderInfo c : sortedInfo) {
			Object[] uses = c.uses;
			for (int j = 0; j < uses.length; j++) {
				Object o = uses[j];
				if (o.getClass() == CoderInfo.class) {
					buf.putVUInt(j+used);
					buf.putVUInt(outputIndex(sortedInfo, (CoderInfo) o));
				} else if (o.getClass() != Integer.class) {
					buf.putVUInt(j+used);
					IntMap.Entry<CoderInfo> entry = Helpers.cast(o);
					buf.putVUInt(outputIndex(sortedInfo, entry.getValue()) + entry.getIntKey());
				} else {
					blocks[(int)o] = j+used;
				}
			}
			used += uses.length;
		}

		// write lengthMap
		for (int block : blocks) buf.putVUInt(block);

		// 最外面写的是uSize,但是实际的streamId是0
		long uSize = b.uSize;
		long _out = b.outSizes[b.outSizes.length-1];
		System.arraycopy(b.outSizes, 0, b.outSizes, 1, b.outSizes.length-1);
		b.outSizes[0] = uSize;
		b.uSize = _out;
	}
	private static int outputIndex(CoderInfo[] coders, CoderInfo value) {
		int sid = 0;
		for (CoderInfo c : coders) {
			if (c == value) break;
			sid += c.provides;
		}
		return sid;
	}

	public int setUSizeId(WordBlock b) {
		assert sizeId >= 0;
		int id = sizeId;
		sizeId = -1;

		CoderInfo[] sortedCodersMy = (CoderInfo[]) b.tmp;
		for (CoderInfo use : sortedCodersMy) {
			if (use.sizeId > id) use.sizeId--;
		}

		return id;
	}
}
