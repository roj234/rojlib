package roj.ci;

import roj.ci.plugin.Plugin;
import roj.collect.ArrayList;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTree;
import roj.collect.TrieTreeSet;
import roj.config.mapper.Optional;
import roj.util.ArtifactVersion;
import roj.util.Helpers;
import roj.util.Pair;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2025/08/29 21:27
 */
@Optional(write = Optional.WriteMode.ALWAYS)
public final class Env {
	static final TrieTreeSet EMPTY_SET = new TrieTreeSet();
	static final LinkedHashMap<String, String> EMPTY_MAP = new LinkedHashMap<>();

	List<Workspace> workspaces = Collections.emptyList();
	List<Project> projects = Collections.emptyList();

	String default_project;
	List<String> auto_compile = Collections.emptyList();

	static final class Project implements Cloneable {
		static final ArtifactVersion DEFAULT_VERSION = new ArtifactVersion("1");

		String name;
		@Optional(nullValue = "roj/ci/Env$Type.PROJECT Lroj/ci/Env$Type;")
		Type type = Type.PROJECT;
		@Optional(nullValue = "roj/ci/Env$Project.DEFAULT_VERSION Lroj/util/ArtifactVersion;")
		ArtifactVersion version = DEFAULT_VERSION;
		@Optional(nullValue = "java/nio/charset/StandardCharsets.UTF_8 Ljava/nio/charset/Charset;")
		Charset charset = StandardCharsets.UTF_8;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		List<String> compiler_options = Collections.emptyList();
		@Deprecated @Optional boolean compiler_options_overwrite;
		String workspace;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		LinkedHashMap<String, Dependency.Scope> dependency = Helpers.cast(EMPTY_MAP);
		@Optional(write = Optional.WriteMode.NON_BLANK)
		LinkedHashMap<String, String> variables = EMPTY_MAP;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		TrieTreeSet variableReplaceContext = EMPTY_SET;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		LinkedHashMap<String, String> shade = EMPTY_MAP;

		void initShade() {
			prefixShades = new TrieTree<>();
			patternShades = new ArrayList<>();
			for (var entry : shade.entrySet()) {
				String pattern = entry.getKey();
				if (pattern.startsWith("/")) {
					patternShades.add(new Pair<>(Pattern.compile(pattern.substring(1)), entry.getValue()));
				} else {
					prefixShades.put(pattern, entry.getValue());
				}
			}
		}

		transient TrieTree<String> prefixShades;
		transient List<Pair<Pattern, String>> patternShades;

		transient Map<String, Dependency> dependencyInstances;
	}

	public static final class Workspace {

		public String id;
		public String type;
		@Optional public String path = "projects";
		@Optional public String inherits;

		@Optional public List<File> depend = Collections.emptyList(), mappedDepend = Collections.emptyList(), unmappedDepend = Collections.emptyList();
		@Optional public List<String> processors = Collections.emptyList();

		@Optional public File mapping;

		@Optional public LinkedHashMap<String, String> variables = EMPTY_MAP;
		@Optional public TrieTreeSet variableReplaceContext = EMPTY_SET;

		void postBuild() {
			if (processors == Collections.EMPTY_LIST)
				processors = new ArrayList<>();
			for (Plugin plugin : MCMake.REGISTERED_PLUGINS) {
				if (plugin.defaultEnabled()) {
					String name = plugin.getClass().getName();
					if (!processors.contains(name)) {
						processors.add(name);
					}
				}
			}
		}
	}

	enum Type {
		/**
		 * 可独立构建的项目
		 */
		PROJECT,
		/**
		 * 不可单独构建、必须作为其他项目一部分的子模块
		 */
		MODULE,
		/**
		 * 本身没有源代码需要编译，仅用于组合PROJECT或MODULE生成自定义Artifact使用
		 */
		ARTIFACT;

		boolean canBuild() {return this != MODULE;}
		boolean needCompile() {return this != ARTIFACT;}
	}
}
