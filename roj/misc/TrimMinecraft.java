package roj.misc;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.ui.CLIUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TrimMinecraft {
	public static void main(String[] args) throws IOException {
		System.out.println("(assets)资源目录");
		String assets = CLIUtil.readString("");
		System.out.println("(libraries)库目录");
		String libraries = CLIUtil.readString("");

		SimpleList<File> versions = new SimpleList<>();
		while (true) {
			System.out.println("(versions)版本目录,可以添加多个,如果没有了请按回车");
			String v = CLIUtil.readString("");
			if (v.isEmpty()) break;
			versions.add(new File(v));
		}

		operate(new File(assets), new File(libraries), versions);
	}

	static void operate(File assets, File libraries, SimpleList<File> versionss) throws IOException {
		MyHashMap<String, File> librariesToRemove = new MyHashMap<>();
		int pathLen = libraries.getAbsolutePath().length() + 1;
		IOUtil.findAllFiles(libraries, file -> {
			librariesToRemove.put(file.getAbsolutePath().substring(pathLen).replace('\\', '/'), file);
			return false;
		});

		MyHashSet<String> assetIds = new MyHashSet<>();
		for (int i = 0; i < versionss.size(); i++) {
			File versions = versionss.get(i);
			File[] files = versions.listFiles();
			if (files == null) {
				CLIUtil.warning(versions+" 不是有效的版本文件夹夹");
				continue;
			}
			for (File version : files) {
				File versionJson = new File(version, version.getName()+".json");
				if (!versionJson.isFile()) {
					CLIUtil.warning(versions+" 不是有效的版本文件夹");
					continue;
				}

				CMapping json;
				try {
					json = new JSONParser().charset(StandardCharsets.UTF_8).parseRaw(versionJson).asMap();
				} catch (ParseException e) {
					CLIUtil.warning(versionJson+" 不是有效的版本JSON");
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
				CLIUtil.warning(file + " 不是有效的资源索引");
				continue;
			}

			CMapping json;
			try {
				json = JSONParser.parses(IOUtil.readUTF(file)).asMap().getOrCreateMap("objects");
			} catch (ParseException e) {
				CLIUtil.warning(file+" 不是有效的资源索引");
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
			System.out.println("按回车执行删除,按L查看文件列表");
			String selection = CLIUtil.readString("");
			switch (selection) {
				case "": break c;
				case "L":
					for (String key : assetsToRemove.keySet()) System.out.println("资源文件 "+key);
					for (String key : librariesToRemove.keySet()) System.out.println("库文件 "+key);
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

		if (error) {
			System.out.println("上述文件未成功删除");
		}
	}
}