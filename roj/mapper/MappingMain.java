package roj.mapper;

import roj.collect.IntSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.mod.mapping.OjngMapping;
import roj.mod.mapping.TSrgMapping;
import roj.mod.mapping.YarnMapping;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.ui.UIUtil;
import roj.util.DynByteBuf;

import java.io.*;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * @author Roj234
 * @since 2022/11/1 17:54
 */
public class MappingMain {
	public static final String LINE = "===========================";
	private static MyHashMap<String, Mapping> loaded = new MyHashMap<>();

	public static void main(String[] args) throws Exception {
		help();

		loop:
		while (true) {
			cout(LINE);

			String cmd = cin("root@114514>");
			if (cmd.isEmpty()) continue;

			myargs.clear();
			TextUtil.split(myargs, cmd, ' ');

			try {
				switch (myargs.remove(0)) {
					case "intr":
					case "I":
						readIntr();
						break;
					case "yarn":
					case "Y":
						readYarn();
						break;
					case "tsrg":
					case "T":
						readTSrg();
						break;
					case "ojng":
					case "M":
						readOJNG();
						break;
					case "xsrg":
					case "S":
						readSrg();
						break;
					case "xscm":
					case "B":
						readBin();
						break;
					case "unload":
					case "u":
						unload();
						break;
					case "save":
					case "s":
						save();
						break;
					case "extend":
					case "e":
						extend();
						break;
					case "display":
					case "d":
						display();
						break;
					case "list":
					case "l":
						list();
						break;
					case "reverse":
					case "r":
						reverse();
						break;
					case "merge":
					case "m":
						merge();
						break;
					case "help":
					case "h":
						help();
						break;
					case "mark":
						mark();
						break;
					case "reset":
						reset();
						break;
					case "exit":
					case "x":
						break loop;
					default:
						cout("不知道你在干啥");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void help() {
		cout("欢迎使用映射表管理客户端1.0");
		cout("操作:\n" +
			"intr(I): 加载intermediary映射表\n" +
			"yarn(Y): 加载yarn映射表\n" +
			"tsrg(T): 加载tsrg映射表(Tabbed Srg)\n" +
			"ojng(M): 加载mojang映射表\n" + "xsrg(S): 加载xsrg映射表(eXtended Srg)\n" +
			"xscm(B): 加载xscm映射表(eXtended Srg compacted)" +
			"\n" +
			"unload (u): 卸载映射表\n" +
			"save   (s): 保存映射表\n" +
			"extend (e): 继承映射表\n" +
			"reverse(r): 反转映射表\n" +
			"merge  (m): 合并映射表\n" +
			"display(d): 显示映射表内容\n" +
			"list   (l): 显示加载的映射表\n" +
			"help   (h): 再显示一遍说明\n" +
			"mark      : 保存当前状态\n" +
			"reset     : 恢复之前状态\n" +
			"exit   (x): 退出");
	}

	private static void readIntr() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		File path = cinf("表");
		YarnMapping m = new YarnMapping();
		m.readIntermediaryMap(path.getName(), new LineReader(IOUtil.readUTF(path)), new SimpleList<>());
		loaded.put(name, m);
	}

	private static void readYarn() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		File path = cinf("zip");
		String mc = cin("MC版本:");
		YarnMapping m = new YarnMapping();
		m.readYarnMap(path, new SimpleList<>(), mc, null);
		loaded.put(name, m);
	}

	private static void readTSrg() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		File path = cinf("表");
		TSrgMapping m = new TSrgMapping();
		m.readMcpConfig(path, null, new SimpleList<>());
		loaded.put(name, m);
	}

	private static void readOJNG() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		File path = cinf("表");
		OjngMapping m = new OjngMapping();
		m.readMojangMap(path.getName(), new LineReader(IOUtil.readUTF(path)), new SimpleList<>());
		loaded.put(name, m);
	}

	private static void readSrg() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		File path = cinf("表");
		Mapping m = new Mapping();
		m.loadMap(path, false);
		loaded.put(name, m);
	}

	private static void readBin() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		File path = cinf("表");
		ConstMapper m = new ConstMapper();
		m.readCache(0, path);
		loaded.put(name, m);
	}

	private static void unload() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		if (null == loaded.remove(name)) cout("不存在");
	}

	private static void save() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		if (null == loaded.get(name)) cout("不存在");
		else {
			String path = cin("表");
			loaded.get(name).saveMap(new File(path));
		}
	}

	private static void extend() throws IOException {
		cout(LINE);
		cout("A extends B");
		String a = cin("A名字:");
		String b = cin("B名字:");
		String c = cin("保存到:");

		if (null == loaded.get(a)) cout("A不存在");
		else if (null == loaded.get(b)) cout("B不存在");
		else {
			Mapping mc = Mapping.fullCopy(loaded.get(a));
			mc.extend(loaded.get(b), cinb("保留A∩B的补集"));
			loaded.put(c, mc);
		}
	}

	private static void reverse() throws IOException {
		cout(LINE);
		String a = cin("名字:");
		String b = cin("保存到:");

		if (null == loaded.get(a)) cout("不存在");
		else {
			loaded.put(b, loaded.get(a).reverse());
		}
	}

	private static void merge() throws IOException {
		cout(LINE);
		cout("结束时,直接按回车");
		Mapping m = new Mapping();
		while (true) {
			String a = cin("名字:");
			if (a.isEmpty()) break;
			if (null == loaded.get(a)) cout("不存在");
			else m.merge(loaded.get(a), true);
		}

		String b = cin("保存到:");
		loaded.put(b, m);
	}

	private static void display() throws IOException {
		cout(LINE);
		String name = cin("名字:");
		if (null == loaded.get(name)) cout("不存在");
		else {
			Random r = new Random();
			Mapping m = loaded.get(name);

			cout("============= CLASS MAPPING =============");
			List<Map.Entry<String, String>> klass = getRandomEntry(m.classMap, r, 10);
			betterOutput(klass);

			cout("============= FIELD MAPPING =============");
			List<Map.Entry<String, String>> field = getRandomEntry(m.fieldMap, r, 10).stream().map((entry) ->
				new AbstractMap.SimpleImmutableEntry<>(entry.getKey().owner + "." + entry.getKey().name, entry.getValue())
			).collect(Collectors.toList());
			betterOutput(field);

			cout("============= METHOD MAPPING =============");
			List<Map.Entry<String, String>> method = getRandomEntry(m.methodMap, r, 10).stream().map((entry) ->
				new AbstractMap.SimpleImmutableEntry<>(entry.getKey().owner + "." + entry.getKey().name, entry.getValue())
			).collect(Collectors.toList());
			betterOutput(method);
		}
	}

	private static void list() {
		cout(LINE);
		betterOutput(loaded.entrySet().stream()
						   .map((entry) -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), String.valueOf(entry.getValue().classMap.size())))
						   .collect(Collectors.toList()));
	}

	private static void mark() throws IOException {
		File file = new File(cin("保存到:"));
		CharList tmp = IOUtil.getSharedCharBuf();

		try (DataOutputStream dos = new DataOutputStream(new DeflaterOutputStream(new FileOutputStream(file)))) {
			dos.writeInt(loaded.size());
			for (Map.Entry<String, Mapping> entry : loaded.entrySet()) {
				dos.writeUTF(entry.getKey());
				entry.getValue().saveMap(tmp);

				dos.writeInt(DynByteBuf.byteCountUTF8(tmp));
				IOUtil.SharedCoder.get().encodeTo(tmp, dos);

				tmp.clear();
			}
		}
	}
	private static void reset() throws IOException {
		File file = cinf("文件");
		CharList tmp = IOUtil.getSharedCharBuf();

		loaded.clear();
		try (DataInputStream dis = new DataInputStream(new InflaterInputStream(new FileInputStream(file)))) {
			int size = dis.readInt();
			while (size-- > 0) {
				String key = dis.readUTF();
				int len = dis.readInt();

				tmp.clear();
				IOUtil.SharedCoder.get().decodeFrom(dis, tmp, len);

				Mapping m = new Mapping();
				m.loadMap(new LineReader(tmp), false);
				loaded.put(key,m);
			}
		}
	}

	private static List<String> myargs = new SimpleList<>();
	private static String cin(String msg) throws IOException {
		return myargs.isEmpty() ? UIUtil.userInput(msg) : myargs.remove(0);
	}
	private static boolean cinb(String msg) throws IOException {
		return myargs.isEmpty() ? UIUtil.readBoolean(msg) : Boolean.parseBoolean(myargs.remove(0));
	}
	private static File cinf(String msg) throws IOException {
		if (!myargs.isEmpty()) {
			File file = new File(myargs.remove(0));
			if (file.exists()) return file;
		}
		return UIUtil.readFile(msg);
	}

	private static void betterOutput(Iterable<Map.Entry<String, String>> klass) {
		int kmax = max(klass, ent -> ent.getKey().length())+1;
		for (Map.Entry<String, String> entry : klass) {
			cout(padded(entry.getKey(), kmax, entry.getValue()));
		}
	}

	private static String padded(Object... data) {
		CharList tmp = IOUtil.getSharedCharBuf();
		if ((data.length & 1) == 0) throw new IllegalArgumentException("data.length("+data.length+") is even");
		int i = 0;
		while (i < data.length) {
			String val = String.valueOf(data[i++]);
			tmp.append(val);
			if (i == data.length) break;
			int pad = (int) data[i++];

			int len = pad - cmdLen(val);
			while (len-- > 0) tmp.append(' ');
		}
		return tmp.toString();
	}

	private static int cmdLen(CharSequence seq) {
		int base = seq.length();
		for (int i = 0; i < seq.length(); i++) {
			if (seq.charAt(i) > 255) base++;
		}
		return base;
	}

	private static <K, V> List<Map.Entry<K, V>> getRandomEntry(Map<K, V> map, Random r, int i) {
		if (map.size() < i) return new SimpleList<>(map.entrySet());
		IntSet id = new IntSet(i);
		while (i-- > 0) while (!id.add(r.nextInt(map.size())));
		List<Map.Entry<K, V>> list = new SimpleList<>(id.size());
		for (Map.Entry<K, V> entry : map.entrySet()) if (id.contains(i++)) list.add(entry);
		return list;
	}

	private static <T> int max(Iterable<T> list, ToIntFunction<T> mapper) {
		int i = Integer.MIN_VALUE;
		for (T t : list) {
			int v = mapper.applyAsInt(t);
			if (v > i) {
				i = v;
			}
		}
		return i;
	}

	private static void cout(String line) {
		System.out.println(line);
	}
}