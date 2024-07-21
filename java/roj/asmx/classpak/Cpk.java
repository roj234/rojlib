package roj.asmx.classpak;

import roj.archive.ArchiveUtils;
import roj.archive.qz.xz.LZMA2Options;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.util.Context;
import roj.asm.util.TransformUtil;
import roj.asmx.mapper.Mapper;
import roj.asmx.nixim.NiximException;
import roj.asmx.nixim.NiximSystemV2;
import roj.collect.Flippable;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.io.DummyOutputStream;
import roj.io.IOUtil;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import static roj.asmx.mapper.Mapper.MF_RENAME_CLASS;

/**
 * @author Roj234
 * @since 2024/3/17 0017 0:49
 */
public class Cpk {
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("Usage: Cpk <input jar> [chunk size]");
			return;
		}

		File input = new File(args[0]);
		File output = IOUtil.deriveOutput(input, ".cpk");
		int chunkSize = args.length > 1 ? Integer.parseInt(args[1]) : 262144;
		main_alt(input, output, chunkSize);
	}
	public static void main_alt(File input, File output, int chunkSize) {
		List<ConstantData> loadable = new SimpleList<>();
		try (ZipFileWriter zo = new ZipFileWriter(output, Deflater.BEST_COMPRESSION, 0)) {
			zo.beginEntry(new ZEntry("META-INF/MANIFEST.MF"));
			zo.write("Manifest-Version: 1.0\nMain-Class: cpk.Main\n".getBytes(StandardCharsets.UTF_8));

			// region lzma2 decoder
			Mapper mapper = new Mapper();

			Flippable<String, String> cMap = mapper.getClassMap();

			List<Context> ctxs = new SimpleList<>();
			int classId = 0;
			ConstantData cpkLoader;
			try (ZipFile self = new ZipFile(IOUtil.getJar(Cpk.class))) {
				cMap.put("roj.archive.qz.xz.rangecoder.RangeCoder".replace('.', '/'), "cpk/A");
				cMap.put("roj.archive.qz.xz.rangecoder.RangeDecoder".replace('.', '/'), "cpk/B");
				cMap.put("roj.archive.qz.xz.lzma.LZMACoder".replace('.', '/'), "cpk/C");
				cMap.put("roj.archive.qz.xz.lzma.LZMADecoder".replace('.', '/'), "cpk/D");

				for (Map.Entry<String, String> entry : cMap.entrySet()) {
					ctxs.add(new Context(entry.getKey(), self.get(entry.getKey().concat(".class"))));
				}

				cMap.put("roj.archive.qz.xz.LZMA2InputStream".replace('.', '/'), "cpk/E");
				cMap.put("roj.archive.qz.xz.lz.LZDecoder".replace('.', '/'), "cpk/F");
				cMap.put("roj.util.ArrayCache".replace('.', '/'), "cpk/G");
				cMap.put("roj.io.CorruptedInputException".replace('.', '/'), "java/io/IOException");
				cMap.put("roj.io.MBInputStream".replace('.', '/'), "java/io/InputStream");

				byte[] bytes = self.get("roj/asmx/classpak/ArrayCacheN_.class");
				ConstantData cd = Parser.parseConstants(bytes);
				cd.name("cpk/G");
				ctxs.add(new Context(cd));

				NiximSystemV2 nx = new NiximSystemV2();

				try {
					byte[] patchBytes = self.get("roj/asmx/classpak/LZDecoderN_.class");
					NiximSystemV2.NiximData patch = nx.read(Parser.parseConstants(patchBytes));

					bytes = self.get("roj/archive/qz/xz/lz/LZDecoder.class");
					cd = Parser.parseConstants(bytes);
					nx.apply(cd, patch);
					ctxs.add(new Context(cd));

					patchBytes = self.get("roj/asmx/classpak/LZMA2InN_.class");
					patch = nx.read(Parser.parseConstants(patchBytes));

					bytes = self.get("roj/archive/qz/xz/LZMA2InputStream.class");
					cd = Parser.parseConstants(bytes);
					nx.apply(cd, patch);
					ctxs.add(new Context(cd));
				} catch (NiximException e) {
					e.printStackTrace();
				}
				cpkLoader = Parser.parseConstants(self.get("roj/asmx/classpak/FakeMain.class"));
				cpkLoader.modifier |= Opcodes.ACC_PUBLIC;

				for (ZEntry entry : self.entries()) {
					String name = entry.getName();
					if (name.startsWith("roj/asmx/classpak/loader/")) {
						cMap.put(name.substring(0, name.length()-6), name.endsWith("CPMain.class") ? "cpk/Main" : "cpk/"+(char)('H'+ classId++));
						ctxs.add(new Context("", self.get(entry)));
					}
				}
			}

			mapper.flag = MF_RENAME_CLASS;
			mapper.map(ctxs);
			int size = ctxs.size();
			for (int i = 0; i < size; i++) {
				Context ctx = ctxs.get(i);
				zo.beginEntry(new ZEntry(ctx.getFileName()));

				ConstantData data = ctx.getData();

				SimpleList<FieldNode> fields = data.fields;
				for (int j = fields.size()-1; j >= 0; j--) {
					if (fields.get(j).attrByName("ConstantValue") != null)
						fields.remove(j);
				}

				TransformUtil.runOnly(data);

				ConstantData altData = TransformUtil.noStackFrameTableEver(data, "cpk/"+(char)('H'+ classId++));
				if (altData != null) ctxs.add(new Context(altData));
				else classId--;

				ctx.getCompressedShared().writeToStream(zo);
				zo.closeEntry();
			}

			for (int i = size; i < ctxs.size(); i++) {
				Context ctx = ctxs.get(i);
				zo.beginEntry(new ZEntry(ctx.getFileName()));
				ctx.getCompressedShared().writeToStream(zo);
				zo.closeEntry();
			}
			// endregion

			Cpk writer = new Cpk(chunkSize);
			String mainClass = null;

			try (ZipFile za = new ZipFile(input)) {
				SimpleList<ZEntry> entries = new SimpleList<>(za.entries());
				entries.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));

				for (ZEntry entry : entries) {
					String name = entry.getName();
					if (name.equals("META-INF/MANIFEST.MF")) {
						// manifset我不会用，谢了
						try (TextReader tr = TextReader.auto(za.getStream(entry))) {
							for (String line : tr) {
								int pos = line.indexOf(": ");
								String attrKey = line.substring(0, pos);
								if (attrKey.equals("Main-Class")) {
									String attrVal = line.substring(pos+2);
									System.out.println("Main-Class: "+attrVal);
									mainClass = attrVal;
								}
							}
						}
						continue;
					}
					if (name.endsWith(".class")) {
						try {
							ConstantData data = Parser.parseConstants(IOUtil.read(za.getStream(entry)));
							if (data.name.concat(".class").equals(name)) {
								loadable.add(data);
								continue;
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					if (ArchiveUtils.INCOMPRESSIBLE_FILE_EXT.contains(IOUtil.extensionName(name))) {
						zo.copy(za, entry);
					} else {
						writer.add(name, za.get(entry));
					}
				}
			}

			cMap.put("roj.asmx.classpak.FakeMain".replace('.', '/'), "cpk/@");
			cMap.put("roj.asmx.classpak.Cpk".replace('.', '/'), mainClass.replace('.', '/'));
			mapper.map(Collections.singletonList(new Context(cpkLoader)));
			writer.add(cpkLoader);

			ZEntry e = new ZEntry("CraftKuro.pak");
			e.setMethod(ZipEntry.STORED);
			zo.beginEntry(e);

			for (ConstantData data : loadable) writer.add(data);

			writer.finish();
			writer.out.writeToStream(zo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public final ByteList out, tmp;
	private final int chunkSize;
	public Cpk(int chunkSize) {
		this.chunkSize = chunkSize;
		this.out = new ByteList();
		this.tmp = new ByteList();
		out.putInt(0).putLong(0).putLong(0).putLong(0);
		files.add(new OffA("", 0));
	}

	private int filePos;
	private final List<OffA> files = new SimpleList<>();

	private static final class OffA {
		private final String name;
		public int cLen, uLen;

		private OffA(String name, int uLen) {
			this.name = name;
			this.uLen = uLen;
		}
	}

	private int cpSize;

	public void add(ConstantData data)  {
		assert !data.name.equals("");

		if (tmp.readableBytes() > chunkSize) flush();

		int pos = tmp.wIndex();

		TransformUtil.compress(data);
		data.getBytes(tmp);

		files.add(new OffA(data.name.replace('/', '.'), tmp.wIndex()-pos));
	}

	public void add(String name, byte[] misc)  {
		if (tmp.readableBytes() > chunkSize) flush();

		tmp.put(misc);
		files.add(new OffA(name, misc.length));
	}

	private int findBestProps(LZMA2Options opt, byte[] data, TaskPool th) {
		AtomicReference<Object[]> ref = new AtomicReference<>();
		for (int lc = 0; lc <= 4; lc++) {
			LZMA2Options copy = opt.clone().setDictSize(Math.max(data.length, 4096)).setLcLp(lc, 0);
			th.submit(() -> {
				DummyOutputStream counter = new DummyOutputStream();
				try (OutputStream os = copy.getOutputStream(counter)) {
					os.write(data);
				} catch (Exception ignored) {}

				Object[] b = new Object[]{copy, counter.wrote};
				while (true) {
					Object[] prev = ref.get();
					if (prev != null && (int)prev[1] <= counter.wrote) return;
					if (ref.compareAndSet(prev, b)) return;
				}
			});
		}

		try {
			th.awaitTermination();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Object[] min = ref.get();
		LZMA2Options best = (LZMA2Options) min[0];
		opt.setLcLp(best.getLc(), best.getLp());
		return (int)min[1];
	}

	private void flush() {
		byte[] dict = tmp.toByteArray();
		int uLen = dict.length;

		LZMA2Options opt = new LZMA2Options(9).setDictSize(Math.max(uLen,4096)).setPb(0).setDepthLimit(999);
		int bestSize = findBestProps(opt, dict, TaskPool.Common());
		System.out.println("Block: #"+ cpSize++ +" ("+opt+") "+uLen+" => "+bestSize+" ("+TextUtil.toFixed(100d * bestSize / uLen, 2)+"%)");

		int cLen = out.wIndex();
		try (OutputStream os = opt.getOutputStream(out)) {
			tmp.writeToStream(os);
		} catch (IOException e) {
			e.printStackTrace();
		}

		tmp.clear();
		OffA a = files.get(filePos);
		a.uLen = uLen;
		a.cLen = out.wIndex() - cLen;
		filePos = files.size();
		files.add(new OffA("", 0));
	}

	public void finish() throws IOException {
		flush();

		int metaPos = out.wIndex();

		ByteList ob = out;
		ob.putVUInt(cpSize).putVUInt(files.size()-1);

		ByteList ob2 = new ByteList();
		for (int i = 0; i < files.size()-1; i++) {
			OffA a = files.get(i);
			if (a.name.isEmpty()) {
				ob2.putVUInt(0).putVUInt(a.cLen).putVUInt(a.uLen);
			} else {
				ob2.putVUIUTF(a.name).putVUInt(a.uLen);
			}
		}
		LZMA2Options opt = new LZMA2Options(9).setDictSize(Math.max(ob2.wIndex(),4096)).setDepthLimit(999);

		int cLen = opt.findBestProps(ob2.toByteArray());
		System.out.println("Strings: "+ob2.wIndex() +" => "+cLen+" ("+TextUtil.toFixed(100d * cLen / ob2.wIndex(), 2)+"%)");

		ob.putVUInt(ob2.wIndex());
		try (OutputStream os = opt.getOutputStream(ob)) {
			ob2.writeToStream(os);
		}

		long mask = System.currentTimeMillis();
		ob.putLong(4, mask);
		ob.putLong(12, metaPos);
		ob.putLong(20, metaPos ^ mask); // simple verify
	}
}