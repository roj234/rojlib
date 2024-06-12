package roj.asmx.classpak;

import roj.archive.ArchiveUtils;
import roj.archive.qz.xz.LZMA2Options;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
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
import roj.io.IOUtil;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import static roj.asmx.mapper.Mapper.MF_RENAME_CLASS;

/**
 * @author Roj234
 * @since 2024/3/17 0017 0:49
 */
public class Cpk {
	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.out.println("Usage: Cpk <input jar> [chunk size]");
			return;
		}

		File input = new File(args[0]);
		File output = new File(input.getParentFile(), IOUtil.fileName(input.getName())+".cpk."+IOUtil.extensionName(input.getName()));
		int chunkSize = args.length > 1 ? Integer.parseInt(args[1]) : 262144;

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
			try (ZipFile myself = new ZipFile(Helpers.getJarByClass(Cpk.class))) {
				cMap.put("roj.archive.qz.xz.rangecoder.RangeCoder".replace('.', '/'), "cpk/A");
				cMap.put("roj.archive.qz.xz.rangecoder.RangeDecoder".replace('.', '/'), "cpk/B");
				cMap.put("roj.archive.qz.xz.lzma.LZMACoder".replace('.', '/'), "cpk/C");
				cMap.put("roj.archive.qz.xz.lzma.LZMADecoder".replace('.', '/'), "cpk/D");

				for (Map.Entry<String, String> entry : cMap.entrySet()) {
					ctxs.add(new Context(entry.getKey(), myself.get(entry.getKey().concat(".class"))));
				}

				cMap.put("roj.archive.qz.xz.LZMA2InputStream".replace('.', '/'), "cpk/E");
				cMap.put("roj.archive.qz.xz.lz.LZDecoder".replace('.', '/'), "cpk/F");
				cMap.put("roj.util.ArrayCache".replace('.', '/'), "cpk/G");
				cMap.put("roj.io.CorruptedInputException".replace('.', '/'), "java/io/IOException");
				cMap.put("roj.io.MBInputStream".replace('.', '/'), "java/io/InputStream");

				byte[] bytes = myself.get("roj/asmx/classpak/ArrayCacheN_.class");
				ConstantData cd = Parser.parseConstants(bytes);
				cd.name("cpk/G");
				ctxs.add(new Context(cd));

				NiximSystemV2 nx = new NiximSystemV2();

				try {
					byte[] patchBytes = myself.get("roj/asmx/classpak/LZDecoderN_.class");
					NiximSystemV2.NiximData patch = nx.read(Parser.parseConstants(patchBytes));

					bytes = myself.get("roj/archive/qz/xz/lz/LZDecoder.class");
					cd = Parser.parseConstants(bytes);
					nx.apply(cd, patch);
					ctxs.add(new Context(cd));

					patchBytes = myself.get("roj/asmx/classpak/LZMA2InN_.class");
					patch = nx.read(Parser.parseConstants(patchBytes));

					bytes = myself.get("roj/archive/qz/xz/LZMA2InputStream.class");
					cd = Parser.parseConstants(bytes);
					nx.apply(cd, patch);
					ctxs.add(new Context(cd));
				} catch (NiximException e) {
					e.printStackTrace();
				}
				cpkLoader = Parser.parseConstants(myself.get("roj/asmx/classpak/FakeMain$Loader.class"));

				for (ZEntry entry : myself.entries()) {
					String name = entry.getName();
					if (name.startsWith("roj/asmx/classpak/loader/")) {
						cMap.put(name.substring(0, name.length()-6), name.endsWith("CPMain.class") ? "cpk/Main" : "cpk/"+(char)('H'+ classId++));
						ctxs.add(new Context("", myself.get(entry)));
					}
				}
			}

			mapper.flag = MF_RENAME_CLASS;
			mapper.map(ctxs);
			int size = ctxs.size();
			for (int i = 0; i < size; i++) {
				Context ctx = ctxs.get(i);
				zo.beginEntry(new ZEntry(ctx.getFileName()));
				System.out.println("Support: "+ctx.getFileName());

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
				System.out.println("Lambda: "+ctx.getFileName());
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

			cMap.put("roj.asmx.classpak.FakeMain$Loader".replace('.', '/'), "cpk/@");
			cMap.put("roj.asmx.classpak.FakeMain".replace('.', '/'), mainClass.replace('.', '/'));
			mapper.map(Collections.singletonList(new Context(cpkLoader)));
			writer.add(cpkLoader);

			ZEntry e = new ZEntry("CraftKuro.pak");
			e.setMethod(ZipEntry.STORED);
			zo.beginEntry(e);

			for (ConstantData data : loadable) writer.add(data);

			writer.finish();
			writer.out.writeToStream(zo);
		}
	}

	public final ByteList out, tmp, tmp2 = new ByteList();
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

	private void flush() {
		byte[] dict = tmp.toByteArray();
		int uLen = dict.length;

		LZMA2Options opt = new LZMA2Options(9).setDictSize(Math.max(uLen,4096)).setDepthLimit(999);
		int bestSize = opt.findBestProps(dict);
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