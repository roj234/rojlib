package roj.ci;

import roj.collect.LinkedHashMap;
import roj.collect.TrieTreeSet;
import roj.config.auto.Optional;
import roj.util.ArtifactVersion;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2025/08/29 21:27
 */
@Optional
final class Env {
	List<Workspace> workspaces = Collections.emptyList();
	List<Project> projects = Collections.emptyList();

	String default_project;
	List<String> auto_compile = Collections.emptyList();

	static final class Project {
		static final ArtifactVersion DEFAULT_VERSION = new ArtifactVersion("1");

		String name;
		@Optional
		Type type = Type.PROJECT;
		@Optional
		ArtifactVersion version = DEFAULT_VERSION;
		@Optional
		Charset charset = StandardCharsets.UTF_8;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		List<String> compiler_options = Collections.emptyList();
		@Deprecated @Optional boolean compiler_options_overwrite;
		String workspace;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		Map<String, Dependency.Scope> dependency = Collections.emptyMap();
		@Optional(write = Optional.WriteMode.NON_BLANK)
		LinkedHashMap<String, String> variables = new LinkedHashMap<>();
		@Optional(write = Optional.WriteMode.NON_BLANK)
		TrieTreeSet variable_replace_in = new TrieTreeSet();
		@Optional(write = Optional.WriteMode.NON_BLANK)
		TrieTreeSet bundle_ignore = new TrieTreeSet();
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
		boolean hasFile() {return this != ARTIFACT;}
	}
}
