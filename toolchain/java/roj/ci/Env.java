package roj.ci;

import roj.collect.LinkedHashMap;
import roj.collect.TrieTreeSet;
import roj.config.mapper.Optional;
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
@Optional(write = Optional.WriteMode.ALWAYS)
final class Env {
	static final TrieTreeSet EMPTY_SET = new TrieTreeSet();
	static final LinkedHashMap<String, String> EMPTY_MAP = new LinkedHashMap<>();

	List<Workspace> workspaces = Collections.emptyList();
	List<Project> projects = Collections.emptyList();

	String default_project;
	List<String> auto_compile = Collections.emptyList();

	static final class Project implements Cloneable {
		static final ArtifactVersion DEFAULT_VERSION = new ArtifactVersion("1");

		String name;
		@Optional(nullValue = "roj.ci.Env.Type.PROJECT")
		Type type = Type.PROJECT;
		@Optional(nullValue = "roj.ci.Env.Project.DEFAULT_VERSION")
		ArtifactVersion version = DEFAULT_VERSION;
		@Optional(nullValue = "java.nio.charset.StandardCharsets.UTF_8")
		Charset charset = StandardCharsets.UTF_8;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		List<String> compiler_options = Collections.emptyList();
		@Deprecated @Optional boolean compiler_options_overwrite;
		String workspace;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		Map<String, Dependency.Scope> dependency = Collections.emptyMap();
		@Optional(write = Optional.WriteMode.NON_BLANK)
		LinkedHashMap<String, String> variables = EMPTY_MAP;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		TrieTreeSet variable_replace_in = EMPTY_SET;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		TrieTreeSet bundle_ignore = EMPTY_SET;
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
