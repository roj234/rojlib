package roj.plugin;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZipFile;
import roj.collect.TrieTreeSet;
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

	String mainClass, moduleId;
	String id, desc = "";
	Version version;
	Charset charset;
	List<String> authors = Collections.emptyList();
	String website = "";
	boolean library;

	Plugin instance;
	List<String> depend = Collections.emptyList(), loadAfter = Collections.emptyList(), loadBefore = Collections.emptyList();
	List<String> javaModuleDepend = Collections.emptyList();
	boolean isModulePlugin;
	transient PluginClassLoader cl;

	// 基于transformer的安全管理
	TrieTreeSet reflectiveClass, extraPath;
	boolean loadNative, dynamicLoadClass, accessUnsafe;
	boolean skipCheck;

	final Object stateLock = new Object();
	volatile int state;

	private PluginDescriptor _next;

	public String getId() { return id; }
	public Version getVersion() { return version; }
	public Plugin instance() { return instance; }
	public int getState() { return state; }
	@Nullable
	public Source getFile() {return source;}
	public ZipFile getArchive() {return cl == null ? null : cl.archive;}

	@Override
	public String toString() { return new CharList().append(id).append(" v").append(version).toStringAndFree(); }
	public String getFullDesc() {
		CharList sb = new CharList();
		sb.append(id).append(" v").append(version).append(" 作者 ").append(authors).append('\n');
		if (!website.isEmpty()) sb.append("网址: ").append(website).append('\n');
		if (!desc.isEmpty()) sb.append("简介:\n  ").append(TextUtil.join(TextUtil.split(desc, '\n'), "\n  ")).append('\n');
		sb.append("状态: ");
		switch (state) {
			case PluginManager.UNLOAD: sb.append("UNLOAD"); break;
			case PluginManager.LOADING: sb.append("LOADING"); break;
			case PluginManager.ERRORED: sb.append("ERRORED"); break;
			case PluginManager.LOADED: sb.append("LOADED"); break;
			case PluginManager.ENABLED: sb.append("ENABLED"); break;
			case PluginManager.DISABLED: sb.append("DISABLED"); break;
		}
		return sb.toStringAndFree();
	}
}