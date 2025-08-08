package roj.ci;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipOutput;
import roj.asm.ClassView;
import roj.asmx.Context;
import roj.collect.HashSet;
import roj.collect.TrieTreeSet;
import roj.collect.XashMap;
import roj.http.curl.DownloadListener;
import roj.io.IOUtil;
import roj.ci.plugin.ProcessEnvironment;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static roj.ci.plugin.ProcessEnvironment.*;
import roj.http.curl.DownloadTask;

/**
 * @author Roj234
 * @since 2025/08/30 03:32
 */
public sealed interface Dependency {
	default @Nullable Project project() {return null;}
	int getResources(Project project, ZipOutput zipOutput, long stamp) throws IOException;
	void getClasses(ProcessEnvironment context, long stamp) throws IOException;

	void getClassPath(CharList classpath, int prefix);

	final class ProjectDep implements Dependency {
		final Project project;

		public ProjectDep(Project project) {this.project = project;}

		@Override
		public @Nullable Project project() {return project;}

		@Override
		public int getResources(Project building, ZipOutput zipOutput, long stamp) {
			var cnt = new AtomicInteger();
			var prev = project.mappedWriter;
			project.mappedWriter = zipOutput;
			IOUtil.listFiles(project.resPath, file -> {
				long lastModified = file.lastModified();
				if (lastModified >= stamp) {
					String relPath = file.getAbsolutePath().substring(project.getResPrefix()).replace(File.separatorChar, '/');
					TrieTreeSet bundleIgnore = building.conf.bundle_ignore;

					boolean blocked;
					if (bundleIgnore.isEmpty()) blocked = relPath.startsWith("META-INF/");
					else {
						blocked = bundleIgnore.strStartsWithThis(relPath);
						if (blocked) {
							String str = relPath + ":" + project.getName();
							int i = bundleIgnore.longestMatches(str);
							if (i > relPath.length()) {
								blocked = i == str.length();
							}
						}
					}

					if (!blocked) {
						project.writeRes(file, lastModified, building.variables);
						cnt.getAndIncrement();
					}
				}
				return false;
			});
			project.mappedWriter = prev;
			return cnt.get();
		}

		@Override
		public void getClasses(ProcessEnvironment context, long stamp) throws IOException {
			long fileTime = project.unmappedJar.lastModified();
			int increment = context.increment;

			if (increment == INC_UPDATE && fileTime < stamp) return;

			try (var archive = project.unmappedWriter.getArchive()) {
				for (ZEntry entry : archive.entries()) {
					String name = entry.getName();
					if (increment != INC_UPDATE || entry.getModificationTime() >= stamp) {
						context.addClass(new Context(name, archive.get(entry)), project,
								increment != INC_LOAD || entry.getModificationTime() >= stamp);
					}
				}
			}
		}

		@Override
		public void getClassPath(CharList classpath, int prefix) {
			classpath.append(project.unmappedJar.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			ProjectDep that = (ProjectDep) o;

			return project == that.project;
		}

		@Override public int hashCode() {return project.hashCode();}
		@Override public String toString() {return project.getName();}
	}

	final class FileDep implements Dependency {
		final File file;
		long lastModified;
		ZipFile archive;
		Set<ZEntry> classes;

		private FileDep _next;

		public FileDep(File file) {
			this.file = file;
		}

		@Override
		public int getResources(Project project, ZipOutput zipOutput, long stamp) throws IOException {
			init();

			int cnt = 0;
			archive.source().reopen();
			for (ZEntry entry : archive.entries()) {
				String name = entry.getName();
				if (name.endsWith("/") || name.startsWith("META-INF/") || classes.contains(entry)) continue;

				if (entry.getModificationTime() >= stamp) {
					if (zipOutput.getWriter() != null) {
						zipOutput.getWriter().copy(archive, entry);
					} else {
						zipOutput.set(entry.getName(), () -> DynByteBuf.wrap(archive.get(entry)), entry.getModificationTime());
					}
					cnt++;
				}
			}
			archive.source().close();
			return cnt;
		}

		private void init() throws IOException {
			if (archive == null) archive = new ZipFile(file);
			else if (file.lastModified() != lastModified) archive.reload();
			else return;

			lastModified = file.lastModified();
			classes = new HashSet<>();
			for (ZEntry entry : archive.entries()) {
				String name = entry.getName();
				if (name.endsWith(".class")) {
					ClassView view = ClassView.parse(DynByteBuf.wrap(archive.get(entry)), false);
					if (view.name.regionMatches(0, name, 0, name.length()-6)) {
						classes.add(entry);
					}
				}
			}
		}

		@Override
		public void getClasses(ProcessEnvironment context, long stamp) throws IOException {
			init();

			archive.source().reopen();
			int increment = context.increment;
			for (ZEntry entry : this.classes) {
				if (increment != INC_UPDATE || entry.getModificationTime() >= stamp) {
					context.addClass(new Context(entry.getName(), archive.get(entry)), null,
							increment != INC_LOAD || entry.getModificationTime() >= stamp);
				}
			}
			archive.source().close();
		}

		@Override
		public void getClassPath(CharList classpath, int prefix) {
			classpath.append(file.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			FileDep fileDep = (FileDep) o;

			return file.equals(fileDep.file);
		}

		@Override public int hashCode() {return file.hashCode();}
		@Override public String toString() {return "FILE:"+file.getName();}
	}

	final class DirDep implements Dependency {
		final File dir;
		static final XashMap.Builder<File, FileDep> BUILDER = XashMap.builder(File.class, FileDep.class, "file", "_next");
		XashMap<File, FileDep> fileDeps = BUILDER.create();

		public DirDep(File dir) {
			this.dir = dir;
		}

		@Override
		public int getResources(Project project, ZipOutput zipOutput, long stamp) throws IOException {
			init();
			int cnt = 0;
			for (FileDep dep : fileDeps) {
				cnt += dep.getResources(project, zipOutput, stamp);
			}
			return cnt;
		}

		private void init() {
			var files = new HashSet<>(IOUtil.listFiles(dir, file -> {
				String ext = IOUtil.extensionName(file.getName());
				return (ext.equals("zip") || ext.equals("jar")) && file.length() != 0;
			}));

			for (var itr = fileDeps.iterator(); itr.hasNext(); ) {
				FileDep dep = itr.next();
				if (!files.remove(dep.file)) {
					IOUtil.closeSilently(dep.archive);
					itr.remove();
				}
			}
			for (File file : files) fileDeps.computeIfAbsent(file);
		}

		@Override
		public void getClasses(ProcessEnvironment context, long stamp) throws IOException {
			for (FileDep dep : fileDeps) {
				dep.getClasses(context, stamp);
			}
		}

		@Override
		public void getClassPath(CharList classpath, int prefix) {
			Predicate<File> callback = file -> {
				String ext = IOUtil.extensionName(file.getName());
				if ((ext.equals("zip") || ext.equals("jar")) && file.length() != 0) {
					classpath.append(file.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
				}
				return false;
			};
			IOUtil.listFiles(dir, callback);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			DirDep dirDep = (DirDep) o;

			return dir.equals(dirDep.dir);
		}

		@Override public int hashCode() {return dir.hashCode();}
		@Override public String toString() {return "DIR:"+dir.getName();}
	}

	final class MavenDep implements Dependency {
		final String mavenId;
		final String mavenPath;
		final FileDep dep;

		public MavenDep(String mavenId) {
			this.mavenId = mavenId;
			this.mavenPath = IOUtil.mavenIdToPath(mavenId).toStringAndFree();
			var cache = new File(FMD.CACHE_PATH, mavenPath);
			cache.getParentFile().mkdirs();
			dep = new FileDep(cache);
		}

		private void init() {
			if (!dep.file.isFile()) {
				try {
					System.out.println("Start downloading "+"https://repo1.maven.org/maven2/"+mavenPath);
					DownloadTask.download("https://repo1.maven.org/maven2/"+mavenPath, dep.file, new DownloadListener.Single());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public int getResources(Project project, ZipOutput zipOutput, long stamp) throws IOException {
			init();
			return dep.getResources(project, zipOutput, stamp);
		}

		@Override
		public void getClasses(ProcessEnvironment context, long stamp) throws IOException {
			init();
			dep.getClasses(context, stamp);
		}

		@Override
		public void getClassPath(CharList classpath, int prefix) {
			init();
			dep.getClassPath(classpath, prefix);
		}
	}

	enum Scope {
		/**
		 * 编译时需要，不传递
		 */
		COMPILE,
		/**
		 * 编译时需要，且传递性依赖（依赖该模块的模块会自动获得其 EXPORT 依赖）
		 */
		EXPORT,
		/**
		 * 打包时需要，包括所有直接和传递的 EXPORT 依赖（即递归包含所有必要的依赖）。
		 */
		BUNDLED;

		public boolean inClasspath() {return true;}
		public boolean exported() {return this == EXPORT;}
		public boolean copyResource() {return ordinal() >= 2;}
	}
}
