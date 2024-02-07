package roj.platform;

import roj.collect.TrieTreeSet;
import roj.config.word.Tokenizer;
import roj.io.source.Source;
import roj.math.Version;
import roj.text.CharList;
import roj.text.TextUtil;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/12/25 0025 15:48
 */
public class PluginDescriptor {
	transient Source source;
	transient String fileName;

	String mainClass;
	String id, desc = "";
	Version version;
	Charset charset;
	List<String> authors = Collections.emptyList();
	String website = "";

	Plugin instance;
	List<String> depend = Collections.emptyList(), loadAfter = Collections.emptyList(), loadBefore = Collections.emptyList();
	transient PluginClassLoader pcl;
	transient ClassLoader cl;

	// 基于transformer的安全管理
	TrieTreeSet reflectiveClass, extraPath;
	boolean loadNative, dynamicLoadClass, accessUnsafe;
	boolean skipCheck;

	volatile int state;

	public String getId() { return id; }
	public Version getVersion() { return version; }
	public Plugin getInstance() { return instance; }

	@Override
	public String toString() { return Tokenizer.addSlashes(id, 0, new CharList("'"), '"').append("' v").append(version).toStringAndFree(); }
	public String getFullDesc() {
		CharList sb = new CharList();
		sb.append("插件 ").append(id).append(" v").append(version).append(" 作者 ").append(authors).append('\n');
		if (!website.isEmpty()) sb.append("网址: ").append(website).append('\n');
		if (!desc.isEmpty()) sb.append("简介:\n  ").append(TextUtil.join(TextUtil.split(desc, '\n'), "\n  ")).append('\n');
		sb.append("状态: ");
		switch (state) {
			case PluginManager.UNLOAD: sb.append("UNLOAD"); break;
			case PluginManager.LOADING: sb.append("LOADING"); break;
			case PluginManager.LOADED: sb.append("LOADED"); break;
			case PluginManager.ENABLED: sb.append("ENABLED"); break;
			case PluginManager.DISABLED: sb.append("DISABLED"); break;
		}
		return sb.toStringAndFree();
	}
}