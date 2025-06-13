package roj.plugins.kuropack;

import roj.archive.ArchiveUtils;
import roj.archive.xz.LZMA2Options;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asmx.Context;
import roj.asmx.TransformUtil;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.injector.WeaveException;
import roj.asmx.mapper.Mapper;
import roj.collect.Flippable;
import roj.collect.ArrayList;
import roj.concurrent.TaskPool;
import roj.io.DummyOutputStream;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.TextUtil;
import roj.ui.Argument;
import roj.ui.Command;
import roj.ui.CommandContext;
import roj.ui.CommandNode;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Manifest;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import static roj.asmx.mapper.Mapper.MF_RENAME_CLASS;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/3/17 0:49
 */
public final class Kuropack {
	public static void main(String[] args) {
		System.out.println("Deprecated. use Panger plugin instead.");

		if (args.length == 0) {
			System.out.println("Usage: Kuropack <input jar> [chunk size]");
			return;
		}

		File input = new File(args[0]);
		File output = IOUtil.deriveOutput(input, ".cpk");
		int chunkSize = args.length > 1 ? Integer.parseInt(args[1]) : 262144;
		pack(input, output, chunkSize);
	}

	@SimplePlugin(id = "kuropack", desc = "CraftKuro™ JAR Packer")
	static final class PluginImpl extends Plugin {
		@Override
		protected void onEnable() {
			Command command = this::run;
			CommandNode chain = argument("分块大小", Argument.dataUnit()).executes(command);
			registerCommand(literal("kuropack").then(argument("输入", Argument.file())
					.executes(command)
					.then(chain)
					.then(argument("输出", Argument.fileOptional(true)).then(chain))
			));
		}

		private void run(CommandContext ctx) {
			var input = ctx.argument("输入", File.class);
			int chunkSize = ctx.argument("分块大小", Integer.class, 10000000);
			var output = ctx.argument("输出", File.class, null);
			if (output == null)
				output = IOUtil.deriveOutput(input, ".cpk");

			System.out.println("正在包装"+input+"到"+output+", 固实大小为"+chunkSize);
			pack(input, output, chunkSize);
			System.out.println("成功");
		}
	}

	public static void pack(File input, File output, int chunkSize) {
		List<ClassNode> loadable = new ArrayList<>();
		try (ZipFileWriter zo = new ZipFileWriter(output, Deflater.BEST_COMPRESSION, 0)) {
			zo.beginEntry(new ZEntry("META-INF/MANIFEST.MF"));
			zo.write("Manifest-Version: 1.0\nMain-Class: cpk.Main\n".getBytes(StandardCharsets.UTF_8));

			// region lzma2 decoder
			Mapper mapper = new Mapper();

			Flippable<String, String> cMap = mapper.getClassMap();

			List<Context> ctxs = new ArrayList<>();
			int classId = 0;
			ClassNode cpkLoader;
			try (ZipFile self = new ZipFile(IOUtil.getJar(Kuropack.class))) {
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

				byte[] bytes = self.get("roj/plugins/kuropack/ArrayCacheN_.class");
				ClassNode cd = ClassNode.parseSkeleton(bytes);
				cd.name("cpk/G");
				ctxs.add(new Context(cd));

				CodeWeaver nx = new CodeWeaver();

				try {
					byte[] patchBytes = self.get("roj/plugins/kuropack/LZDecoderN_.class");
					CodeWeaver.Patch patch = nx.read(ClassNode.parseSkeleton(patchBytes));

					bytes = self.get("roj/archive/xz/lz/LZDecoder.class");
					cd = ClassNode.parseSkeleton(bytes);
					CodeWeaver.patch(cd, patch);
					ctxs.add(new Context(cd));

					patchBytes = self.get("roj/plugins/kuropack/LZMA2InN_.class");
					patch = nx.read(ClassNode.parseSkeleton(patchBytes));

					bytes = self.get("roj/archive/xz/LZMA2InputStream.class");
					cd = ClassNode.parseSkeleton(bytes);
					CodeWeaver.patch(cd, patch);
					ctxs.add(new Context(cd));
				} catch (WeaveException e) {
					e.printStackTrace();
				}
				cpkLoader = ClassNode.parseSkeleton(self.get("roj/plugins/kuropack/FakeMain.class"));
				cpkLoader.modifier |= Opcodes.ACC_PUBLIC;

				for (ZEntry entry : self.entries()) {
					String name = entry.getName();
					if (name.startsWith("roj/plugins/kuropack/loader/")) {
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

				ClassNode data = ctx.getData();

				ArrayList<FieldNode> fields = data.fields;
				for (int j = fields.size()-1; j >= 0; j--) {
					if (fields.get(j).getRawAttribute("ConstantValue") != null)
						fields.remove(j);
				}

				TransformUtil.runOnly(data);

				ClassNode altData = TransformUtil.noStackFrameTableEver(data, "cpk/"+(char)('H'+ classId++));
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

			Kuropack writer = new Kuropack(chunkSize);
			String mainClass = null;

			try (ZipFile za = new ZipFile(input)) {
				ArrayList<ZEntry> entries = new ArrayList<>(za.entries());
				entries.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));

				for (ZEntry entry : entries) {
					String name = entry.getName();
					if (name.equals("META-INF/MANIFEST.MF")) {
						try (var in = za.getStream(entry)) {
							var mf = new Manifest(in);
							mainClass = mf.getMainAttributes().getValue("Main-Class");
						}
						continue;
					}
					if (name.endsWith(".class")) {
						try {
							ClassNode data = ClassNode.parseSkeleton(IOUtil.read(za.getStream(entry)));
							if (data.name().concat(".class").equals(name)) {
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

			cMap.put("roj.plugins.kuropack.FakeMain".replace('.', '/'), "cpk/@");
			cMap.put("roj.plugins.kuropack.Cpk".replace('.', '/'), mainClass.replace('.', '/'));
			mapper.map(Collections.singletonList(new Context(cpkLoader)));
			writer.add(cpkLoader);

			ZEntry e = new ZEntry("CraftKuro.pak");
			e.setMethod(ZipEntry.STORED);
			zo.beginEntry(e);

			for (ClassNode data : loadable) writer.add(data);

			writer.finish();
			writer.out.writeToStream(zo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public final ByteList out, tmp;
	private final int chunkSize;
	public Kuropack(int chunkSize) {
		this.chunkSize = chunkSize;
		this.out = new ByteList();
		this.tmp = new ByteList();
		out.putInt(0).putLong(0).putLong(0).putLong(0);
		files.add(new OffA("", 0));
	}

	private int filePos;
	private final List<OffA> files = new ArrayList<>();

	private static final class OffA {
		private final String name;
		public int cLen, uLen;

		private OffA(String name, int uLen) {
			this.name = name;
			this.uLen = uLen;
		}
	}

	private int cpSize;

	public void add(ClassNode data)  {
		assert !data.name().equals("");

		if (tmp.readableBytes() > chunkSize) flush();

		int pos = tmp.wIndex();

		TransformUtil.compress(data);
		data.toByteArray(tmp);

		files.add(new OffA(data.name().replace('/', '.'), tmp.wIndex()-pos));
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

		LZMA2Options opt = new LZMA2Options(9).setDictSize(Math.max(Math.min(1048576, uLen),4096)).setPb(0).setDepthLimit(999);
		int bestSize = findBestProps(opt, dict, TaskPool.common());
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