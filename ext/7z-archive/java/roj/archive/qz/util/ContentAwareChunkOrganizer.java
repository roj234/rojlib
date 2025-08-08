package roj.archive.qz.util;

import roj.collect.ArrayList;
import roj.collect.ToDoubleEntry;
import roj.collect.XashMap;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.ui.EasyProgressBar;
import roj.util.Helpers;
import roj.util.function.Flow;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 压缩块2-pass重排序算法
 * 付出一定的预处理时间和O(n * SMALL_CONSTANT)的内存甚至可以降低接近10%的压缩后大小，很强的！参考什么百分比扣血（bushi
 * @author Roj234-N
 * @since 2025/5/15 15:31
 */
final class ContentAwareChunkOrganizer {
	/**
	 * Organizes files into content-aware chunks for optimal compression.
	 *
	 * @param files List of files to be organized into chunks
	 * @param targetChunkSize The desired <b>approximate</b> size for each output chunk (in bytes)
	 * @return List of file groups where each group should be compressed together
	 * @throws IOException if file access fails during processing
	 */
	public static List<List<File>> organizeChunks(List<File> files, long targetChunkSize) {
		var blockHash = new BlockHash(101, 4096, 1024);
		var chunks = new ArrayList<List<File>>();

		var sources = new ArrayList<Source>();
		XashMap<String, Cluster> clustersMap = Helpers.cast(XashMap.noCreation(Cluster.class, "file").create());

		try (var bar = new EasyProgressBar("")) {
			bar.setTotal(files.size());
			for (File file : files) {
				try (var source = new FileSource(file, false)) {
					sources.add(source);

					clustersMap.add(new Cluster(source.toString(), file.length()));

					// 首先进行块级去重
					var matches = blockHash.addAndFind(source);
					for (int i = 0; i < matches.size(); i++) {
						BlockHash.Match match = matches.get(i);
						clustersMap.get(match.other.toString()).addSimilarityTo(clustersMap.get(source.toString()), match.length);
						clustersMap.get(source.toString()).addSimilarityTo(clustersMap.get(match.other.toString()), match.length);
					}

					bar.setName(file.getName()+"["+matches.size()+"]");
				} catch (IOException e) {
					e.printStackTrace();
				}
				bar.increment();
			}
		}

		for (Source file : sources) IOUtil.closeSilently(file);

		List<Cluster> clusters = new ArrayList<>(clustersMap);
		merge(clusters, targetChunkSize + 1048576);
		clusters = Flow.of(clusters).filter(c -> c.alive).toList();
		mergeSmall(clusters, targetChunkSize - 1048576, targetChunkSize);

		for (Cluster cluster : clusters) {
			if (!cluster.alive) continue;

			System.out.println("Cluster "+cluster+"+"+cluster.combined.size()+": Size="+cluster.totalSize+", Similarity="+cluster.internalSimilarity);

			var chunk = new ArrayList<File>();
			chunks.add(chunk);

			chunk.add(new File(cluster.file));
			for (Cluster other : cluster.combined) {
				chunk.add(new File(other.file));
			}

			files.removeAll(chunk);
		}

		// add rest files
		chunks.add(files);
		return chunks;
	}

	private static void merge(List<Cluster> clusters, long maxBlockSize) {
		PriorityQueue<Candidate> queue = new PriorityQueue<>();
		// 初始化队列，添加所有相邻簇对
		for (Cluster c1 : clusters) {
			for (var entry : c1.adjacency) {
				Cluster c2 = entry.getKey();
				if (c1.file.compareTo(c2.file) < 0) { // 避免重复添加
					queue.add(new Candidate(c1, c2, entry.value));
				}
			}
		}

		while (!queue.isEmpty()) {
			var pair = queue.poll();
			var a = pair.a;
			var b = pair.b;
			if (!a.alive || !b.alive) continue;

			double mergedValue = a.totalSize + b.totalSize;
			if (mergedValue <= maxBlockSize) {
				a.combine(b, pair.similarityScore);
				// 将新簇的邻接关系加入队列
				for (var entry : a.adjacency) {
					Cluster neighbor = entry.getKey();
					queue.add(new Candidate(a, neighbor, entry.value));
				}
			}
		}
	}
	private static void mergeSmall(List<Cluster> clusters, long minBlockSize, long maxBlockSize) {
		for (int num = 0; num < 10; num++) {
			// 使用列表存储小簇，避免O(n²)队列
			List<Cluster> smallClusters = clusters.stream()
					.filter(c -> c.alive && c.totalSize < minBlockSize)
					.sorted(Comparator.comparingLong(c -> c.totalSize))
					.toList();

			// 贪心合并：按大小排序，合并 smallest first
			for (int i = 0; i < smallClusters.size(); i++) {
				Cluster c1 = smallClusters.get(i);
				if (!c1.alive) continue;
				for (int j = i + 1; j < smallClusters.size(); j++) {
					Cluster c2 = smallClusters.get(j);
					if (!c2.alive) continue;
					if (c1.totalSize + c2.totalSize <= maxBlockSize) {
						c1.combine(c2, 0); // 假设边权为0，因为非邻接
						c2.alive = false;
						break; // 合并后跳出内层循环
					}
				}
			}
		}
	}

	private static final class Candidate implements Comparable<Candidate> {
		Cluster a, b;
		double similarityScore;

		public Candidate(Cluster a, Cluster b, double similarityScore) {
			this.a = a;
			this.b = b;
			this.similarityScore = similarityScore;
		}

		@Override
		public int compareTo(Candidate other) {return Double.compare(other.similarityScore, this.similarityScore);}
	}

	private static final class Cluster {
		Cluster _next;

		String file;
		List<Cluster> combined = new ArrayList<>();
		long totalSize;

		double internalSimilarity;
		XashMap<Cluster, ToDoubleEntry<Cluster>> adjacency; // 邻接簇及其边权总和

		boolean alive;

		public Cluster(String file, long size) {
			this.file = file;
			this.totalSize = size;
			this.internalSimilarity = 0;
			this.adjacency = ToDoubleEntry.newMap();
			this.alive = true;
		}

		public void combine(Cluster other, double similarityScore) {
			this.totalSize += other.totalSize;
			this.internalSimilarity += other.internalSimilarity + similarityScore;
			other.alive = false;

			combined.add(other);
			combined.addAll(other.combined);

			// 合并邻接表
			for (var entry : other.adjacency) {
				Cluster neighbor = entry.getKey();
				if (neighbor == this) continue;

				double weight = entry.value;

				addSimilarityTo(neighbor, weight);
				neighbor.adjacency.removeKey(other);
				neighbor.addSimilarityTo(this, weight);
			}
			this.adjacency.removeKey(other);
		}

		public void addSimilarityTo(Cluster owner, double weight) {
			adjacency.computeIfAbsent(owner).value += weight;
		}

		@Override
		public String toString() {
			return file;
		}
	}
}

