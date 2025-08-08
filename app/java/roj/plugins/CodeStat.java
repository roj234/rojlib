package roj.plugins;

import roj.collect.HashMap;
import roj.collect.ArrayList;
import roj.collect.ToIntMap;
import roj.util.function.Flow;
import roj.gui.GuiUtil;
import roj.gui.TextAreaPrintStream;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.ui.Argument;
import roj.ui.CommandNode;
import roj.ui.Tty;

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

/**
 * @author Roj234
 * @since 2022/10/7 22:32
 */
@SimplePlugin(id = "codeStat", desc = "让你可以直观的看到自己写了多少代码\n指令: codestat [文件夹]", version = "2.1")
public class CodeStat extends Plugin {
	private static final Function<String, ToIntMap<String>> compute = (x) -> new ToIntMap<>();
	private final HashMap<String, ToIntMap<String>> lines = new HashMap<>(), chars = new HashMap<>();
	private final ToIntMap<String> totalLines = new ToIntMap<>(), totalChars = new ToIntMap<>();
	private static boolean hasUI;

	@Override
	protected void onEnable() throws Exception {
		registerCommand(CommandNode.literal("codestat").executes(ctx -> {
			main(new String[0]);
		}).then(CommandNode.argument("path", Argument.path()).executes(ctx -> {
			main(new String[] {ctx.argument("path", String.class)});
		})));
	}

	public static void main(String[] args) throws IOException {
		if (!Tty.IS_RICH) {
			if (!hasUI) {
				hasUI = true;
				GuiUtil.systemLaf();
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
		File f = new File(args.length == 0 ? "projects/rojlib/java" : args[0]);
		if (!f.isDirectory()) {
			File[] f1 = GuiUtil.filesLoadFrom("选择文件夹以统计所有子目录(可多选)", null, JFileChooser.DIRECTORIES_ONLY);
			if (f1 == null) return;
			files = Arrays.asList(f1);
		} else {
			files = Collections.singletonList(f);
		}
		System.out.println("正在计算...");

		CodeStat inst = new CodeStat();
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
					String ext = IOUtil.extensionName(name);
					switch (ext) {
						case "java", "go", "c", "cpp", "h", "hpp", "js", "jsx", "ts", "py":
						case "php", "asp", "jsp", "html", "aspx", "xhtml", "htm", "css":
						case "less", "scss", "sass", "vue", "lua", "kt", "rs", "mjs":
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
			totalLinesX += entry.value;
		}
		long totalCharsX = 0;
		for (ToIntMap.Entry<String> entry : inst.totalChars.selfEntrySet()) {
			totalCharsX += entry.value;
		}
		int fileCount = 0;
		for (ToIntMap<String> value : inst.lines.values()) {
			fileCount += value.size();
		}

		System.out.println("你已经写了 "+totalLinesX+" 行 (合 " +totalCharsX+" 字符, "+fileCount+" 文件) 的代码呢~");
		if (inst.totalLines.size() > 1) {
			printStatistic(inst, "语言按行数", new ArrayList<>(inst.totalLines.selfEntrySet()), 99);
			printStatistic(inst, "语言按字符数", new ArrayList<>(inst.totalChars.selfEntrySet()), 99);
			printStatistic(inst, "语言按文件数", Flow.of(inst.lines.entrySet()).map(entry -> new ToIntMap.Entry<>(entry.getKey(), entry.getValue().size())).toList(), 99);
			for (Map.Entry<String, ToIntMap<String>> entry : inst.lines.entrySet()) {
				printStatistic(inst, "行数Top"+TOP+" - "+entry.getKey(), new ArrayList<>(entry.getValue().selfEntrySet()), TOP);
			}
			for (Map.Entry<String, ToIntMap<String>> entry : inst.chars.entrySet()) {
				printStatistic(inst, "字符Top"+TOP+" - "+entry.getKey(), new ArrayList<>(entry.getValue().selfEntrySet()), TOP);
			}
		}
		ArrayList<ToIntMap.Entry<String>> all = new ArrayList<>();
		for (Map.Entry<String, ToIntMap<String>> entry : inst.lines.entrySet())
			all.addAll(entry.getValue().selfEntrySet());
		printStatistic(inst, "行数Top"+TOP, all, TOP);
		all.clear();
		for (Map.Entry<String, ToIntMap<String>> entry : inst.chars.entrySet())
			all.addAll(entry.getValue().selfEntrySet());
		printStatistic(inst, "字符Top"+TOP, all, TOP);
	}

	private static void printStatistic(CodeStat inst, String title, List<ToIntMap.Entry<String>> list, int limit) {
		System.out.println("========= "+title+" ============");
		list.sort((o1, o2) -> Integer.compare(o2.value, o1.value));
		double sum = 0;
		for (int i = 0; i < list.size(); i++) sum += list.get(i).value;
		sum /= 100;
		for (int i = 0; i < Math.min(list.size(), limit); i++) {
			System.out.println((i + 1) + ". " + list.get(i).getKey() + ": " + list.get(i).value + " (" + TextUtil.toFixed(list.get(i).value / sum, 2) + "%)");
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
		totalLines.increment(ext, lines);
		totalChars.increment(ext, chars);
	}
}