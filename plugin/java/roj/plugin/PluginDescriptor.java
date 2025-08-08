package roj.plugin;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZipFile;
import roj.asmx.Transformer;
import roj.collect.TrieTreeSet;
import roj.io.source.Source;
import roj.util.ArtifactVersion;
import roj.text.CharList;
import roj.text.TextUtil;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/12/25 15:48
 */
public class PluginDescriptor {
	public enum Role {
		PermissionManager(true);

		public final boolean singleton;
		Role(boolean singleton) {
			this.singleton = singleton;
		}
	}

	transient Source source;
	transient String fileName;

	String mainClass, moduleId;
	String id, desc = "";
	ArtifactVersion version;
	Charset charset;
	List<String> authors = Collections.emptyList();
	String website = "";
	boolean library;
	Role role;

	Plugin instance;
	List<String> depend = Collections.emptyList(), loadAfter = Collections.emptyList(), loadBefore = Collections.emptyList();
	List<String> javaModuleDepend = Collections.emptyList();
	boolean isModulePlugin;
	transient PluginClassLoader classLoader;

	// 基于transformer的安全管理
	TrieTreeSet reflectiveClass, extraPath;
	boolean loadNative, dynamicLoadClass, accessUnsafe;
	boolean skipCheck;

	final Object stateLock = new Object();
	volatile int state;

	private PluginDescriptor _next;

	public PluginClassLoader getClassLoader() {return classLoader;}
	public void addTransformer(Transformer transformer) {
		if (transformer.getClass().getClassLoader() != PluginDescriptor.class.getClassLoader())
			throw new IllegalArgumentException("不允许");
		classLoader.transformers.add(transformer);
	}

	public String getId() { return id; }
	public ArtifactVersion getVersion() { return version; }
	public Plugin instance() { return instance; }
	public int getState() { return state; }
	@Nullable
	public Source getFile() {return source;}
	public ZipFile getArchive() {return classLoader == null ? null : classLoader.archive;}

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