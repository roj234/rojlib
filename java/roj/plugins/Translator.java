package roj.plugins;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.Parser;
import roj.asm.cp.Constant;
import roj.asm.cp.CstString;
import roj.asm.tree.ConstantData;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextWriter;
import roj.ui.terminal.Argument;
import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2020/10/18 14:46
 */
@SimplePlugin(id = "translator", desc = """
	Jar和Class常量字符串替换(翻译)工具
	生成翻译映射表: translate 映射表 jar/class/文件夹
	    ...使用文本编辑器修改映射表中的字符串...
	应用翻译映射表: translate apply 映射表 jar/class/文件夹""")
public class Translator extends Plugin {
	private static Map<String, IntMap<String>> value;
	private static TextWriter out1;

	@Override
	@SuppressWarnings("unchecked")
	protected void onEnable() throws Exception {
		registerCommand(literal("translate").then(literal("apply").then(argument("dict", Argument.file()).then(argument("jar", Argument.files(0)).executes(ctx -> {
			value = new MyHashMap<>();
			Tokenizer wr = new Tokenizer();
			wr.tokenIds(Tokenizer.generate(": 11\n= 12"))
			  .literalEnd(MyBitSet.from(":="))
			  .init(TextReader.auto(ctx.argument("dict", File.class)));

			Word w = wr.except(Word.LITERAL, "类名");
			while (wr.hasNext()) {
				String name = w.val();
				wr.except(11, ":");

				IntMap<String> map = new IntMap<>();

				w = wr.except(Word.INTEGER, "常量池索引");
				while (wr.hasNext()) {
					int pos = w.asInt();
					wr.except(12, "=");
					w = wr.except(Word.STRING, "翻译字符串");
					map.putInt(pos, w.val());

					w = wr.readWord();
					if (w.type() != Word.INTEGER) break;
				}

				value.put(name, map);
			}

			List<File> jar = ctx.argument("jar", List.class);
			for (File file : jar) apply(file);
		})))).then(argument("dict", Argument.fileOptional(true)).then(argument("jar", Argument.files(0)).executes(ctx -> {
			try (TextWriter out = TextWriter.to(ctx.argument("dict", File.class))) {
				List<File> jar = ctx.argument("jar", List.class);
				out1 = out;
				for (File file : jar) read(file);
				out1 = null;
			}
			getLogger().info("字符映射表保存成功");
		}))));
	}

	private static void apply(File f) throws IOException {
		if (f.isDirectory()) {
			IOUtil.findAllFiles(f, file -> {
				String name = file.getName().toLowerCase(Locale.ROOT);
				if (name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".class")) {
					try {
						apply(file);
					} catch (IOException e) {
						System.out.println("错误，这个文件没法读取: " + file.getAbsolutePath());
						e.printStackTrace();
					}
				}
				return false;
			});
		} else if (f.isFile()) {
			ByteList ib = IOUtil.getSharedByteBuf();

			if (f.getName().endsWith(".class")) {
				byte[] mod = apply("", ib.readStreamFully(new FileInputStream(f)));
				if (mod != null) {
					System.out.println("已修改的内容: " + f.getName());
					try (FileOutputStream fos = new FileOutputStream(f)) {
						fos.write(mod);
					}
				}
			} else {
				try (ZipArchive mzf = new ZipArchive(f)) {
					mzf.setComment("Roj234's class translator");

					String path = f.getName()+'/';
					for (ZEntry file : mzf.entries()) {
						String name = file.getName();
						if (name.endsWith(".class")) {
							ib.clear();

							byte[] mod = apply(path, mzf.get(file, ib));
							if (mod != null) {
								System.out.println("已修改的内容: " + path+name);
								mzf.put(name, new ByteList(mod));
							}
						} else if (name.endsWith("/")) {
							mzf.put(name, null);
						}
					}

					mzf.save();
				}
			}
		}
	}
	private static byte[] apply(String path, ByteList ib) {
		ConstantData data = Parser.parseConstants(ib);

		IntMap<String> map = value.get(path+data.name);
		if (map == null) return null;

		boolean any = false;

		List<Constant> array = data.cp.array();
		for (IntMap.Entry<String> entry : map.selfEntrySet()) {
			CstString ref = (CstString) array.get(entry.getIntKey()-1);
			if (!ref.name().str().equals(entry.getValue())) {
				ref.setValue(data.cp.getUtf(entry.getValue()));
				any = true;
			}
		}

		return any ? Parser.toByteArray(data) : null;
	}

	private static void read(File f) throws IOException {
		if (f.isDirectory()) {
			IOUtil.findAllFiles(f, file -> {
				String name = file.getName().toLowerCase(Locale.ROOT);
				if (name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".class")) {
					try {
						read(file);
					} catch (IOException e) {
						System.out.println("错误，这个文件没法读取: " + file.getAbsolutePath());
						e.printStackTrace();
					}
				}
				return false;
			});
		} else if (f.isFile()) {
			ByteList buf = IOUtil.getSharedByteBuf();
			if (f.getName().endsWith(".class")) {
				read("", buf.readStreamFully(new FileInputStream(f)));
			} else {
				try (ZipFile zf = new ZipFile(f)) {
					String path = f.getName()+'/';
					Enumeration<? extends ZipEntry> e = zf.entries();
					while (e.hasMoreElements()) {
						ZipEntry ze = e.nextElement();
						if (ze.getName().endsWith(".class")) {
							buf.clear();
							read(path, buf.readStreamFully(zf.getInputStream(ze)));
						}
					}
				}
			}
		}
	}
	private static void read(String path, ByteList ib) {
		ConstantData data = Parser.parseConstants(ib);

		CharList sb = IOUtil.getSharedCharBuf();
		sb.append(path).append(data.name).append(':').append('\n');
		boolean any = false;

		List<Constant> array = data.cp.array();
		for (int i = 0; i < array.size(); i++) {
			Constant s = array.get(i);
			if (s.type() == Constant.STRING) {
				sb.append('\t').append(s.getIndex()).append('=').append('"');
				Tokenizer.addSlashes(sb, ((CstString) s).name().str()).append('"').append('\n');

				any = true;
			}
		}

		if (any) out1.append(sb);
	}
}