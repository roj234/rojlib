package roj.asmx.misc;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.Parser;
import roj.asm.cp.Constant;
import roj.asm.cp.CstString;
import roj.asm.tree.ConstantData;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextWriter;
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

/**
 * @author Roj234
 * @since 2020/10/18 14:46
 */
public class Translator {
	private static Map<String, IntMap<String>> value;
	private static TextWriter out1;

	public static void main(String[] args) throws IOException, ParseException {
		if (args.length < 2) {
			System.out.println("Translator [-apply] <file>... <dictionary>");
			return;
		}

		if (args[0].equals("-apply")) {
			value = new MyHashMap<>();
			parse(args);

			for (int i = 1; i < args.length - 1; i++) apply(new File(args[i]));
			return;
		}

		try (TextWriter out = TextWriter.to(new File(args[args.length-1]))) {
			out1 = out;
			for (int i = 0; i < args.length - 1; i++) read(new File(args[i]));
			out1 = null;
		}
	}

	private static void parse(String[] args) throws IOException, ParseException {
		Tokenizer wr = new Tokenizer();
		wr.tokenIds(Tokenizer.generate(": 11\n= 12"))
		  .literalEnd(MyBitSet.from(":="))
		  .init(TextReader.auto(new File(args[args.length-1])));

		Word w = wr.except(Word.STRING, "Class Name");
		while (wr.hasNext()) {
			String name = w.val();
			wr.except(11, ":");

			IntMap<String> map = new IntMap<>();

			w = wr.except(Word.INTEGER, "CST String Id");
			while (wr.hasNext()) {
				int pos = w.asInt();
				wr.except(12, "=");
				w = wr.except(Word.STRING, "Quoted I18n value");
				map.putInt(pos, w.val());

				w = wr.readWord();
				if (w.type() != Word.INTEGER) break;
			}

			value.put(name, map);
		}
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
					mzf.getEND().setComment("Roj234's class translator");

					String path = f.getName()+'/';
					for (ZEntry file : mzf.getEntries().values()) {
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

					mzf.store();
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
	private static void read(String path, ByteList ib) throws IOException {
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