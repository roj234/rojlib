package roj.ci;

import org.jetbrains.annotations.Nullable;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.config.ConfigMaster;
import roj.config.MsgPackEncoder;
import roj.config.XmlParser;
import roj.config.node.xml.Document;
import roj.config.node.xml.Node;
import roj.http.HttpRequest;
import roj.http.curl.DownloadListener;
import roj.http.curl.DownloadTask;
import roj.io.IOUtil;
import roj.text.DateFormat;
import roj.text.ParseException;
import roj.text.TextUtil;
import roj.util.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2025/10/09 23:08
 */
public class MavenCoordinate {
	public final String groupId, artifactId, classifier, packaging;
	public final VersionRange version;

	public MavenCoordinate(String coordinate) {
		List<String> parts = TextUtil.split(new ArrayList<>(4), coordinate, ':');

		String ext = "jar";
		String lastPart = parts.get(parts.size() - 1);
		int extPos = lastPart.lastIndexOf('@');
		if (extPos != -1) {
			ext = lastPart.substring(extPos + 1);
			parts.set(parts.size() - 1, lastPart.substring(0, extPos));
		}

		groupId = parts.get(0);
		artifactId = parts.get(1);
		version = VersionRange.parse(parts.get(2));
		classifier = parts.size() > 3 ? parts.get(3) : "";

		packaging = ext;
	}

	private static final String CHECKSUM = "SHA-1";
	private Metadata metadata;

	public void setChecksum(byte[] checksum) {
		if (!version.isSingleVersion()) {
			throw new IllegalStateException("You may only specify checksum for a single version");
		}

		if (metadata == null) {
			metadata = new Metadata();
			metadata.checksum = new HashMap<>();
		}
		metadata.checksum.put(version.getLowerBound(), checksum);
	}

	private static final class Metadata {
		String urlHash;
		String baseUrl;
		long lastUpdated, lastModified;
		String latestVersion;
		Map<ArtifactVersion, byte[]> checksum;
		List<ArtifactVersion> versions;

		transient Set<ArtifactVersion> checksumPassed = new HashSet<>();
	}

	public File locate(File repoBase, List<String> candidateUrls, int updatePeriodMinute, boolean downloadNow) throws IOException {
		var localMetadataFile = new File(repoBase, IOUtil.getSharedCharBuf().append(groupId).append('/').append(artifactId).append("/metadata.msg").toString());

		var metadata = this.metadata;
		foundValidMetadata: {
			String urlHash = makeUrlHash(candidateUrls);

			if (localMetadataFile.isFile()) {
				try {
					if (metadata == null || metadata.urlHash == null) {
						var newMetadata = MCMake.CONFIG.read(localMetadataFile, Metadata.class, ConfigMaster.MSGPACK);
						if (metadata != null) newMetadata.checksum.putAll(metadata.checksum);
						metadata = newMetadata;
					}

					if (urlHash.equals(metadata.urlHash) && (
						version.isSingleVersion() || (System.currentTimeMillis() - metadata.lastUpdated) / 60000 <= updatePeriodMinute
					)) {
						break foundValidMetadata;
					}
				} catch (Exception e) {
					MCMake.log.error("Failed to read {}'s metadata", e, this);
				}
			}

			Metadata remoteMetadata = null;

			if (metadata != null && urlHash.equals(metadata.urlHash))
				remoteMetadata = tryDownloadRemoteMetadata(urlHash, metadata.baseUrl);

			if (remoteMetadata == null) {
				if (!downloadNow) return null;
				remoteMetadata = findRemoteMetadata(urlHash, candidateUrls);
			}

			// 假设maven的文件是immutable的
			if (metadata != null) remoteMetadata.checksum.putAll(metadata.checksum);

			saveMetadata(localMetadataFile, remoteMetadata);

			this.metadata = metadata = remoteMetadata;
		}

		ArtifactVersion chosenVersion = version.isSingleVersion() ? version.getUpperBound() : version.selectHighest(metadata.versions);
		if (chosenVersion == null) throw new IllegalStateException("对于工件 "+this+" 的限制 "+version+", 找不到合适的版本");

		var localCache = new File(repoBase, artifactPath(chosenVersion, false));
		var fileExist = localCache.isFile();
		if (fileExist && !metadata.checksumPassed.contains(chosenVersion)) {
			byte[] expectedChecksum = metadata.checksum.get(chosenVersion);
			if (expectedChecksum != null && !Dependency.verifyFile(CHECKSUM, localCache, expectedChecksum, toString())) {
				Files.delete(localCache.toPath());
				fileExist = false;
			} else {
				metadata.checksumPassed.add(chosenVersion);
			}
		}

		if (!fileExist) {
			try {
				var task = DownloadTask.createTask(metadata.baseUrl+artifactPath(chosenVersion, true), localCache, new DownloadListener.Single());
				task.chunkStart = Integer.MAX_VALUE; // 强制单线程
				task.run();
				task.get();

				String checksum = task.client.response().get("x-checksum-sha1");
				if (checksum != null) {
					metadata.checksum.put(chosenVersion, IOUtil.decodeHex(checksum));
					saveMetadata(localMetadataFile, metadata);
				}

				if (!Dependency.verifyFile(CHECKSUM, localCache, metadata.checksum.get(chosenVersion), toString())) {
					Files.deleteIfExists(localCache.toPath());
					throw new FastFailException("Checksum error!");
				}
			} catch (Exception e) {
				throw new IllegalStateException("Failed to download artifact "+chosenVersion+" for "+this, e);
			}
		}

		return localCache;
	}

	private void saveMetadata(File localMetadataFile, Metadata finalRemoteMetadata) throws IOException {
		IOUtil.writeFileEvenMoreSafe(localMetadataFile.getParentFile(), localMetadataFile.getName(), file -> {
			try (var out = new FileOutputStream(file)) {
				ByteList bb = IOUtil.getSharedByteBuf();
				MCMake.CONFIG.write(new MsgPackEncoder.Compressed(bb), finalRemoteMetadata);
				bb.writeToStream(out);
			} catch (Exception e) {
				MCMake.log.error("Failed to save {}'s metadata", e, this);
			}
		});
	}

	private  String artifactPath(ArtifactVersion version, boolean isUrl) {
		var sb = IOUtil.getSharedCharBuf().append(groupId).replace(isUrl ? '.' : '/', '/').append('/').append(artifactId).append('/').append(version).append('/').append(artifactId).append('-').append(version);
		if (!classifier.isEmpty()) sb.append('-').append(classifier);
		return sb.append('.').append(packaging).toString();
	}

	private Metadata findRemoteMetadata(String urlHash, List<String> candidateUrls) {
		for (int i = 0; i < candidateUrls.size(); i++) {
			var uri = candidateUrls.get(i);
			Metadata metadata = tryDownloadRemoteMetadata(urlHash, uri);
			if (metadata != null) return metadata;
		}
		throw new IllegalStateException("未在任何Maven仓库候选中定位到工件 "+this);
	}

	private @Nullable Metadata tryDownloadRemoteMetadata(String urlHash, String baseUrl) {
		String metadataUrl = IOUtil.getSharedCharBuf().append(baseUrl).append(groupId).replace('.', '/', baseUrl.length(), baseUrl.length()+groupId.length()).append('/').append(artifactId).append("/maven-metadata.xml").toString();
		try {
			var response = HttpRequest.builder().uri(metadataUrl).executePooled();
			if (response.statusCode() == 200) {
				String lastModStr = response.headers().get("last-modified");
				long lastModified;
				if (lastModStr == null) {
					MCMake.log.warn("Source {} does not provide last-modified header.", baseUrl);
					lastModified = 0;
				} else {
					lastModified = DateFormat.parseRFC5322Datetime(lastModStr);
				}

				var xmlMeta = (Document)new XmlParser().parse(response.stream());

				var groupId = xmlMeta.querySelector("/metadata/groupId").textContent();
				var artifactId = xmlMeta.querySelector("/metadata/artifactId").textContent();
				if (!groupId.equals(this.groupId) || !artifactId.equals(this.artifactId)) {
					throw new FastFailException("Remote repository groupId/artifactId doesn't match.");
				}

				var metadata = new Metadata();
				metadata.baseUrl = baseUrl;
				metadata.urlHash = urlHash;
				metadata.lastUpdated = System.currentTimeMillis();
				metadata.lastModified = lastModified;
				metadata.latestVersion = xmlMeta.querySelector("/metadata/versioning/latest").textContent();
				metadata.checksum = new HashMap<>();
				metadata.versions = new ArrayList<>();

				var versions = xmlMeta.querySelector("/metadata/versioning/versions").children();
				for (Node version : versions) metadata.versions.add(new ArtifactVersion(version.textContent()));

				return metadata;
			} else {
				MCMake.log.debug("status code {} for {}", response, metadataUrl);
			}
		} catch (ParseException | RuntimeException e) {
			MCMake.log.warn("无法解析Maven仓库 {} 的响应", e, metadataUrl);
		} catch (IOException e) {
			MCMake.log.debug("无法读取Maven仓库 {} 的响应", e, metadataUrl);
		}
		return null;
	}

	private static String makeUrlHash(List<String> candidateUrls) {return Helpers.sha1Hash(TextUtil.join(candidateUrls, "\0"));}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MavenCoordinate that = (MavenCoordinate) o;
		return groupId.equals(that.groupId) && artifactId.equals(that.artifactId) && classifier.equals(that.classifier) && packaging.equals(that.packaging) && version.equals(that.version);
	}

	@Override
	public int hashCode() {
		int result = groupId.hashCode();
		result = 31 * result + artifactId.hashCode();
		result = 31 * result + classifier.hashCode();
		result = 31 * result + packaging.hashCode();
		result = 31 * result + version.hashCode();
		return result;
	}

	@Override
	public String toString() {return groupId+":"+artifactId+":"+version;}
}
