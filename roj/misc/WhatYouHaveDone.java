package roj.misc;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.ui.GUIUtil;
import roj.ui.TextAreaPrintStream;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Roj234
 * @since 2022/10/7 0007 22:32
 */
public class WhatYouHaveDone {
	private static final Function<String, ToIntMap<String>> compute = (x) -> new ToIntMap<>();
	private final MyHashMap<String, ToIntMap<String>> lines = new MyHashMap<>(), chars = new MyHashMap<>();
	private final ToIntMap<String> totalLines = new ToIntMap<>(), totalChars = new ToIntMap<>();
	private static boolean hasUI;

	public static void main(String[] args) throws IOException {
		if (!CLIUtil.ANSI) {
			if (!hasUI) {
				hasUI = true;
				GUIUtil.systemLook();
				JFrame frame = new JFrame() {
					{
						JScrollPane scrollPane1 = new JScrollPane();
						JTextArea textArea1 = new JTextArea();
						System.setOut(new TextAreaPrintStream(textArea1, 99999));
						JButton button1 = new JButton();

						//======== this ========
						setTitle("New JFrame Layout");
						Container contentPane = getContentPane();
						contentPane.setLayout(new BorderLayout());

						//======== scrollPane1 ========
						scrollPane1.setViewportView(textArea1);
						contentPane.add(scrollPane1, BorderLayout.CENTER);

						//---- button1 ----
						button1.setText("open");
						contentPane.add(button1, BorderLayout.NORTH);
						button1.addActionListener(e -> {
							try {
								main(new String[0]);
							} catch (IOException ex) {
								ex.printStackTrace();
							}
						});
						pack();
						setLocationRelativeTo(getOwner());
						setSize(500, 700);
					}
				};
				frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
				frame.setVisible(true);
				return;
			}
		}

		List<File> files;
		File f = new File(args.length == 0 ? "D:\\mc\\FMD-1.5.2\\projects\\implib\\java" : args[0]);
		if (!f.isDirectory()) {
			File[] f1 = GUIUtil.filesLoadFrom("选择文件夹以统计所有子目录(可多选)", null, JFileChooser.DIRECTORIES_ONLY);
			if (f1 == null) return;
			files = Arrays.asList(f1);
		} else {
			files = Collections.singletonList(f);
		}
		System.out.println("正在计算...");

		WhatYouHaveDone inst = new WhatYouHaveDone();
		for (File file : files) {
			Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (dir.endsWith("node_modules") || dir.endsWith("site-packages")) return FileVisitResult.SKIP_SUBTREE;
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					String name = file.getFileName().toString();
					String ext = IOUtil.extensionName(name).toLowerCase();
					switch (ext) {
						case "java": case "go": case "c": case "cpp": case "h": case "hpp": case "js": case "jsx": case "ts": case "py":
						case "php": case "asp": case "jsp": case "html": case "aspx": case "xhtml": case "htm": case "css":
						case "less": case "scss": case "sass": case "vue": case "lua": case "kt": case "rs": case "mjs":
							inst.accept(name, ext, file);
							break;
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}

		boolean noLimit = Boolean.getBoolean("noLimit");
		int TOP = noLimit ? Integer.MAX_VALUE : 10;
		long totalLinesX = 0;
		for (ToIntMap.Entry<String> entry : inst.totalLines.selfEntrySet()) {
			totalLinesX += entry.v;
		}
		long totalCharsX = 0;
		for (ToIntMap.Entry<String> entry : inst.totalChars.selfEntrySet()) {
			totalCharsX += entry.v;
		}
		int fileCount = 0;
		for (ToIntMap<String> value : inst.lines.values()) {
			fileCount += value.size();
		}

		System.out.println("你已经写了 "+totalLinesX+" 行 (合 " +totalCharsX+" 字符, "+fileCount+" 文件) 的代码呢~");
		if (inst.totalLines.size() > 1) {
			printStatistic(inst, "语言按行数", new SimpleList<>(inst.totalLines.selfEntrySet()), 99);
			printStatistic(inst, "语言按字符数", new SimpleList<>(inst.totalChars.selfEntrySet()), 99);
			printStatistic(inst, "语言按文件数", inst.lines.entrySet().stream().map(entry -> new ToIntMap.Entry<>(entry.getKey(), entry.getValue().size())).collect(Collectors.toList()), 99);
			for (Map.Entry<String, ToIntMap<String>> entry : inst.lines.entrySet()) {
				printStatistic(inst, "行数Top"+TOP+" - "+entry.getKey(), new SimpleList<>(entry.getValue().selfEntrySet()), TOP);
			}
			for (Map.Entry<String, ToIntMap<String>> entry : inst.chars.entrySet()) {
				printStatistic(inst, "字符Top"+TOP+" - "+entry.getKey(), new SimpleList<>(entry.getValue().selfEntrySet()), TOP);
			}
		}
		SimpleList<ToIntMap.Entry<String>> all = new SimpleList<>();
		for (Map.Entry<String, ToIntMap<String>> entry : inst.lines.entrySet())
			all.addAll(entry.getValue().selfEntrySet());
		printStatistic(inst, "行数Top"+TOP, all, TOP);
		all.clear();
		for (Map.Entry<String, ToIntMap<String>> entry : inst.chars.entrySet())
			all.addAll(entry.getValue().selfEntrySet());
		printStatistic(inst, "字符Top"+TOP, all, TOP);
	}

	private static void printStatistic(WhatYouHaveDone inst, String title, List<ToIntMap.Entry<String>> list, int limit) {
		System.out.println("========= "+title+" ============");
		list.sort((o1, o2) -> Integer.compare(o2.v, o1.v));
		double sum = 0;
		for (int i = 0; i < list.size(); i++) sum += list.get(i).v;
		sum /= 100;
		for (int i = 0; i < Math.min(list.size(), limit); i++) {
			System.out.println((i + 1) + ". " + list.get(i).k + ": " + list.get(i).v + " (" + TextUtil.toFixed(list.get(i).v / sum, 2) + "%)");
		}
		System.out.println("========= "+title+" 结束 ============");
	}

	private void accept(String name, String ext, Path file) {
		int lines = 0, chars = 0;
		try (TextReader in = TextReader.auto(file)) {
			CharList sb = IOUtil.getSharedCharBuf();
			while (in.readLine(sb)) {
				if (sb.length() > 0) {
					lines++;
					chars += sb.length();
				}
				sb.clear();
			}
		} catch (IOException e) {
			System.out.println(file);
			e.printStackTrace();
		}

		this.lines.computeIfAbsent(ext, compute).put(name, lines);
		this.chars.computeIfAbsent(ext, compute).put(name, chars);
		totalLines.increase(ext, lines);
		totalChars.increase(ext, chars);
	}
}