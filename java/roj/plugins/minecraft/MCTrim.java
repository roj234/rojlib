package roj.plugins.minecraft;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.CharList;
import roj.text.TextWriter;
import roj.ui.Argument;
import roj.ui.Shell;
import roj.ui.Terminal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

@SimplePlugin(id = "mcTrim", desc = "删除Minecraft中不被引用的资源", version = "1.3")
public class MCTrim extends Plugin {
	@Override
	protected void onEnable() throws Exception {
		registerCommand(literal("mctrim").then(argument("assets", Argument.folder()).then(argument("libraries", Argument.folder()).executes(ctx -> {
			File assets = ctx.argument("assets", File.class).getAbsoluteFile();
			File libraries = ctx.argument("libraries", File.class).getAbsoluteFile();
			System.out.println(libraries);

			var c = new Shell("");

			c.setPrompt("\u001b[;97m添加MC版本目录,Ctrl+C以结束 > ");
			c.setInputEcho(true);

			SimpleList<File> versions = new SimpleList<>();
			while (true) {
				File file = Terminal.readLine(c, Argument.folder());
				if (file == null) break;
				versions.add(file);
			}
			if (versions.isEmpty()) return;

			operate(assets, libraries, versions);
		}))));
	}

	static void operate(File assets, File libraries, SimpleList<File> versionss) throws IOException {
		MyHashMap<String, File> librariesToRemove = new MyHashMap<>();
		int pathLen = libraries.getAbsolutePath().length() + 1;
		IOUtil.findAllFiles(libraries, file -> {
			librariesToRemove.put(file.getAbsolutePath().substring(pathLen).replace(File.separatorChar, '/'), file);
			return false;
		});

		MyHashSet<String> assetIds = new MyHashSet<>();
		for (int i = 0; i < versionss.size(); i++) {
			File versions = versionss.get(i);
			File[] files = versions.listFiles();
			if (files == null) {
				Terminal.warning(versions+" 不是有效的版本文件夹夹");
				continue;
			}
			for (File version : files) {
				File versionJson = new File(version, version.getName()+".json");
				if (!versionJson.isFile()) {
					Terminal.warning(versions+" 不是有效的版本文件夹");
					continue;
				}

				CMap json;
				try {
					json = new JSONParser().charset(StandardCharsets.UTF_8).parse(versionJson).asMap();
				} catch (ParseException e) {
					Terminal.warning(versionJson+" 不是有效的版本JSON");
					continue;
				}
				json.dot(true);
				String id = json.getString("assetIndex.id");
				// inheritFrom
				if (!id.isEmpty()) assetIds.add(id);
				CList libraries1 = json.getOrCreateList("libraries");
				for (int j = 0; j < libraries1.size(); j++) {
					String name = libraries1.get(j).asMap().getString("name");
					CharList maven = IOUtil.mavenPath(name);
					librariesToRemove.remove(maven);
				}
			}
		}

		MyHashMap<String, File> assetsToRemove = new MyHashMap<>();
		IOUtil.findAllFiles(new File(assets, "objects"), file -> {
			assetsToRemove.put(file.getName(), file);
			return false;
		});

		for (String assetId : assetIds) {
			File file = new File(assets, "indexes/"+assetId+".json");
			if (!file.isFile()) {
				Terminal.warning(file + " 不是有效的资源索引");
				continue;
			}

			CMap json;
			try {
				json = new JSONParser().parse(file).asMap().getMap("objects");
			} catch (ParseException e) {
				Terminal.warning(file+" 不是有效的资源索引");
				continue;
			}
			for (CEntry value : json.values()) {
				assetsToRemove.remove(value.asMap().getString("hash"));
			}
		}

		System.out.println(librariesToRemove.size()+" 个未被使用的库");
		System.out.println(assetsToRemove.size()+" 个未被使用的资源");

		c:
		while (true) {
			var selection = Terminal.readChar("LlSs\n", "回车 删除, L 查看列表, S 保存列表, Ctrl+C 取消");
			switch (selection) {
				case 0: return;
				case '\n': break c;
				case 'L', 'l':
					for (String key : assetsToRemove.keySet()) System.out.println("资源文件 "+key);
					for (String key : librariesToRemove.keySet()) System.out.println("库文件 "+key);
					break;
				case 'S', 's':
					File file = Terminal.readLine(new Shell("保存到 > "), Argument.fileOptional(true));
					try (var tw = TextWriter.to(file)) {
						tw.append("assets\n");
						for (String key : assetsToRemove.keySet())  tw.append(key).append('\n');
						tw.append("libraries\n");
						for (String key : librariesToRemove.keySet()) tw.append(key).append('\n');
					}
				break;
			}
		}

		boolean error = false;
		for (File file : librariesToRemove.values()) {
			if (!file.delete()) {
				System.out.println(file.getAbsolutePath());
				error = true;
			}
		}

		for (File file : assetsToRemove.values()) {
			if (!file.delete()) {
				System.out.println(file.getAbsolutePath());
				error = true;
			}
		}

		IOUtil.removeEmptyPaths(assets);
		IOUtil.removeEmptyPaths(libraries);

		if (error) System.out.println("上述文件未成功删除");
	}
}