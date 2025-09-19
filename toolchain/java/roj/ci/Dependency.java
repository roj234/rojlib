package roj.ci;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipOutput;
import roj.asm.ClassView;
import roj.asmx.AnnotationRepo;
import roj.asmx.Context;
import roj.ci.event.LibraryModifiedEvent;
import roj.collect.HashSet;
import roj.collect.TrieTreeSet;
import roj.collect.XashMap;
import roj.http.curl.DownloadListener;
import roj.http.curl.DownloadTask;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static roj.ci.BuildContext.*;

/**
 * @author Roj234
 * @since 2025/08/30 03:32
 */
public sealed interface Dependency {
	default @Nullable Project project() {return null;}
	int getResources(Project project, ZipOutput zipOutput, BuildContext ctx) throws IOException;
	void getClasses(BuildContext context) throws IOException;
	AnnotationRepo getAnnotations(BuildContext context) throws IOException;

	void getClassPath(CharList classpath, int prefix);

	static boolean verifyFile(String checksumAlgorithm, File file, byte[] expectedChecksum, String id) {
		try {
			MessageDigest digest = MessageDigest.getInstance(checksumAlgorithm);
			IOUtil.digestFile(file, file.length(), digest);
			byte[] checksum = digest.digest();
			if (!Arrays.equals(checksum, expectedChecksum)) {
				MCMake.LOGGER.warn("文件校验失败: {} (期望: {}, 实际: {})", id, IOUtil.encodeHex(expectedChecksum), IOUtil.encodeHex(checksum));
				return false;
			}
		} catch (Exception e) {
			MCMake.LOGGER.warn("无法计算文件校验和: {}", e, id);
			return false;
		}

		return true;
	}

	final class ProjectDep implements Dependency {
		final Project project;

		public ProjectDep(Project project) {this.project = project;}

		@Override
		public @Nullable Project project() {return project;}

		@Override
		public int getResources(Project building, ZipOutput writer, BuildContext ctx) {
			int cnt = 0;
			var prev = project.mappedWriter;
			project.mappedWriter = writer;
			try {
				BuildContext.Changeset changeset = ctx.increment <= INC_REBUILD ? project.compiling.getFullResources() : project.compiling.resources;

				List<File> changed = changeset.getChanged();
				Set<String> deleted = changeset.getDeleted();

				MCMake.LOGGER.debug("Dependency mode={} change {} delete {}", ctx.increment, changed.size(), deleted.size());

				long useCompileTimestamp = project.shouldUseCompileTimestamp() ? ctx.buildStartTime : 0;
				TrieTreeSet bundleIgnore = building.conf.bundle_ignore;

				for (File file : changed) {
					String relPath = file.getAbsolutePath().substring(project.getResPrefix()).replace(File.separatorChar, '/');
					if (!isIgnored(bundleIgnore, relPath)) {
						project.writeRes(file, useCompileTimestamp != 0 ? useCompileTimestamp : file.lastModified(), building.variables, ctx);
						cnt++;
					}
				}

				for (String relPath : deleted) {
					if (!isIgnored(bundleIgnore, relPath)) {
						writer.set(relPath, (ByteList) null);
						cnt++;
					}
				}
			} catch (IOException e) {
				MCMake.LOGGER.warn("Exception writing resources for ProjectDep {}", e, project);
			} finally {
				project.mappedWriter = prev;
			}
			return cnt;
		}

		private boolean isIgnored(TrieTreeSet bundleIgnore, String relPath) {
			boolean ignored;
			if (bundleIgnore.isEmpty()) ignored = relPath.startsWith("META-INF/");
			else {
				ignored = bundleIgnore.strStartsWithThis(relPath);
				if (ignored) {
					String str = relPath + ":" + project.getName();
					int i = bundleIgnore.longestMatches(str);
					if (i > relPath.length()) {
						ignored = i == str.length();
					}
				}
			}
			return ignored;
		}

		@Override
		public void getClasses(BuildContext context) throws IOException {
			long stamp = context.buildStartTime;
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
		public AnnotationRepo getAnnotations(BuildContext context) {return project.annotationRepo;}

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
		AnnotationRepo repo;

		private FileDep _next;

		public FileDep(File file) {
			this.file = file;
		}

		private void init(BuildContext ctx) throws IOException {
			if (archive == null) archive = new ZipFile(file);
			else if (file.lastModified() != lastModified) {
				archive.reload();
				MCMake.EVENT_BUS.post(new LibraryModifiedEvent(ctx.project));
				repo = null;
			} else return;

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
		public int getResources(Project project, ZipOutput zipOutput, BuildContext ctx) throws IOException {
			init(ctx);

			int updated = 0;
			long lastBuildTime = ctx.increment <= INC_REBUILD ? 0 : ctx.lastBuildTime;
			archive.source().reopen();
			for (ZEntry entry : archive.entries()) {
				String name = entry.getName();
				if (name.endsWith("/") || name.startsWith("META-INF/") || classes.contains(entry)) continue;

				if (entry.getModificationTime() >= lastBuildTime) {
					if (zipOutput.getWriter() != null) {
						zipOutput.getWriter().copy(archive, entry);
					} else {
						zipOutput.set(entry.getName(), () -> DynByteBuf.wrap(archive.get(entry)), entry.getModificationTime());
					}
				}
				updated++;
			}
			archive.source().close();
			return updated;
		}

		@Override
		public void getClasses(BuildContext ctx) throws IOException {
			init(ctx);

			archive.source().reopen();
			int increment = ctx.increment;
			long lastBuildTime = ctx.lastBuildTime;

			for (ZEntry entry : classes) {
				if (increment != INC_UPDATE || entry.getModificationTime() >= lastBuildTime) {
					ctx.addClass(new Context(entry.getName(), archive.get(entry)), null,
							increment != INC_LOAD || entry.getModificationTime() >= lastBuildTime);
				}
			}
			archive.source().close();
		}

		@Override
		public AnnotationRepo getAnnotations(BuildContext context) throws IOException {
			init(context);

			if (repo == null) {
				archive.source().reopen();
				repo = new AnnotationRepo();
				repo.loadCacheOrAdd(archive);
				archive.source().close();
			}
			return repo;
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
		AnnotationRepo repo;

		public DirDep(File dir) {
			this.dir = dir;
		}

		@Override
		public int getResources(Project project, ZipOutput zipOutput, BuildContext ctx) throws IOException {
			init();
			int cnt = 0;
			for (FileDep dep : fileDeps) {
				cnt += dep.getResources(project, zipOutput, ctx);
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
		public void getClasses(BuildContext context) throws IOException {
			for (FileDep dep : fileDeps) {
				dep.getClasses(context);
			}
		}

		@Override
		public void getClassPath(CharList classpath, int prefix) {
			Predicate<File> callback = jarFilter(classpath, prefix);
			IOUtil.listFiles(dir, callback);
		}

		@Override
		public AnnotationRepo getAnnotations(BuildContext context) throws IOException {
			if (repo == null) {
				init();

				repo = new AnnotationRepo();
				for (FileDep dep : fileDeps) {
					dep.archive.source().reopen();
					repo.loadCacheOrAdd(dep.archive);
					dep.archive.source().close();
				}
			}
			return repo;
		}

		public static Predicate<File> jarFilter(CharList classpath, int prefix) {
			 return file -> {
				String ext = IOUtil.extensionName(file.getName());
				if ((ext.equals("zip") || ext.equals("jar")) && file.length() != 0) {
					classpath.append(file.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
				}
				return false;
			};
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
		final Project owner;
		final String dependencyId;

		final String mavenId;
		final String mavenPath;
		final FileDep cache;

		String checksumAlgorithm;
		byte[] expectedChecksum;
		final List<String> mavenUrls;
		boolean checksumPassed;

		public MavenDep(Project owner, String dependencyId, String mavenId, String checksumAlgorithm, byte[] expectedChecksum) {
			this.owner = owner;
			this.dependencyId = dependencyId;

			this.mavenId = mavenId;
			this.mavenPath = IOUtil.mavenIdToPath(mavenId).toStringAndFree();
			this.checksumAlgorithm = checksumAlgorithm;
			this.expectedChecksum = expectedChecksum;
			this.mavenUrls = TextUtil.split(owner.variables.getOrDefault("fmd:maven:central", "https://repo1.maven.org/maven2/"), ';');

			String path = owner.variables.get("fmd:maven:cache");
			File cacheFile = path != null ? new File(path+"/"+mavenPath) : new File(MCMake.CACHE_PATH, ".m2/"+mavenPath);

			cache = new FileDep(cacheFile);
		}

		private void downloadFile() {
			File cacheFile = cache.file;
			if (!cacheFile.isFile() || (checksumAlgorithm != null && !checksumPassed && !verifyFile(checksumAlgorithm, cacheFile, expectedChecksum, mavenId))) {
				try {
					if (!Files.deleteIfExists(cacheFile.toPath())) {
						Files.createDirectories(cacheFile.getParentFile().toPath());
					}
				} catch (IOException e) {
					Helpers.athrow(e);
				}

				Throwable cause = null;
				for (String mavenCentral : mavenUrls) {
					try {
						var task = DownloadTask.createTask(mavenCentral+mavenPath, cacheFile, new DownloadListener.Single());
						task.chunkStart = Integer.MAX_VALUE;
						task.run();
						task.get();

						String checksum = task.client.response().get("x-checksum-sha1");
						if (checksum != null) {
							if (checksumAlgorithm == null) {
								checksumAlgorithm = "SHA-1";
								expectedChecksum = IOUtil.decodeHex(checksum);

								URI u = URI.create(dependencyId);
								String newDependencyId = new URI(u.getScheme(),
										"sha1", checksum, u.getPort(),
										u.getPath(), u.getQuery(),
										u.getFragment()).toString();
								MCMake.LOGGER.debug("NewDependencyId: {}", newDependencyId);

								Scope scope = owner.conf.dependency.remove(dependencyId);
								owner.conf.dependency.put(newDependencyId, scope);
							}
						}

						if (checksumAlgorithm == null || verifyFile(checksumAlgorithm, cacheFile, expectedChecksum, mavenId)) {
							checksumPassed = true;
							return;
						}
						Files.deleteIfExists(cacheFile.toPath());
					} catch (ExecutionException e) {
						cause = e;
					} catch (Exception e) {
						Helpers.athrow(e);
					}
				}

				Helpers.athrow(cause);
			}
		}

		@Override
		public int getResources(Project project, ZipOutput zipOutput, BuildContext ctx) throws IOException {
			downloadFile();
			return cache.getResources(project, zipOutput, ctx);
		}

		@Override
		public void getClasses(BuildContext context) throws IOException {
			downloadFile();
			cache.getClasses(context);
		}

		@Override
		public AnnotationRepo getAnnotations(BuildContext context) throws IOException {
			downloadFile();
			return cache.getAnnotations(context);
		}

		@Override
		public void getClassPath(CharList classpath, int prefix) {
			downloadFile();
			cache.getClassPath(classpath, prefix);
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
