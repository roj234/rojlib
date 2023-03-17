package roj.mod.fp;

import roj.collect.MyHashSet;
import roj.collect.TrieTree;

import java.io.File;

/**
 * @author Roj234
 * @since 2023/1/19 11:23
 */
public final class Fabric extends WorkspaceBuilder {
	@Override
	public String getId() {
		return "fabric";
	}

	File fabricApi, remappedJar;

	@Override
	void loadLibraries1(File root, TrieTree<String> artifacts) {
		// 帮我们下好了，不错
		String intMap = get1(artifacts, "net/fabricmc/intermediary/");
		String loader = get1(artifacts, "net/fabricmc/fabric-loader/");

		String jarName = ".fabric/remappedJars/minecraft-"+getVersion(intMap)+"-"+getVersion(loader)+"/client-intermediary.jar";
		File f = new File(root.getParentFile(), jarName);
		if (!f.isFile()) f = new File(jsonPath.getParentFile(), jarName);
		if (!f.isFile()) throw new IllegalStateException("无法找到client-intermediary在"+f.getAbsolutePath());

		file.putInt(0, f);
		file.putInt(10, new File(root, loader));
		int v = 11;

		// new File(root, get1(artifacts, "lzma/lzma"))
		// this.file.s
		skipLib = new MyHashSet<>(intMap);
	}
	private static String getVersion(String s) {
		int i = s.lastIndexOf('/');
		int j = s.lastIndexOf('/', i-1);
		return s.substring(j+1, i);
	}

	public Fabric() {}

	public void run() {

	}
}
