package roj.gui.impl;

import roj.collect.SimpleList;
import roj.collect.XToDouble;
import roj.collect.XashMap;
import roj.io.IOUtil;
import roj.io.RollingHashDedup;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 压缩块2-pass重排序算法，WIP
 * @author Roj234-N
 * @since 2025/5/15 15:31
 */
public class BlockCombiner {
	public static List<List<File>> twoPass(List<File> compressed, long chunkSize) {
		var rollingHash = new RollingHashDedup(101, 4096, 1024);
		long totalSize = 0;

		var out = new SimpleList<List<File>>();

		var files = new SimpleList<Source>();
		try {
			XashMap<String, Cluster> nodes = Helpers.cast(XashMap.noCreation(Cluster.class, "id").create());
			for (File file : compressed) {
				try {
					var source = new FileSource(file, false);
					totalSize += source.length();
					files.add(source);

					nodes.add(new Cluster(source.toString(), file.length()));

					List<RollingHashDedup.Match> matches = rollingHash.deduplicate(source);
					for (int i = 0; i < matches.size(); i++) {
						RollingHashDedup.Match match = matches.get(i);
						nodes.get(match.other.toString()).increment(nodes.get(source.toString()), -match.length);
						nodes.get(source.toString()).increment(nodes.get(match.other.toString()), -match.length);
					}

					//source.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			for (Source file : files) {
				IOUtil.closeSilently(file);
			}

			List<Cluster> clusters = new SimpleList<>(nodes);
			clusters = merge(clusters, chunkSize + 1048576);
			mergeTinyGlobal(clusters, chunkSize, chunkSize - 1048576, chunkSize);

			// 输出结果
			clusters.stream().filter(c -> c.alive).forEach(c -> System.out.println("Cluster " + c + "," + c.combined + ": Value=" + c.totalValue + ", Edges=" + c.internalEdgeSum));

			for (Cluster cluster : clusters) {
				if (!cluster.alive) continue;
				var ob = new SimpleList<File>();
				out.add(ob);
				ob.add(new File(cluster.id));
				for (Cluster cluster1 : cluster.combined) {
					ob.add(new File(cluster1.id));
				}

				compressed.removeAll(ob);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println(compressed.size());
		out.add(compressed);
		return out;
	}

	static List<Cluster> merge(List<Cluster> clusters, long maxBlockSize) {
		PriorityQueue<Candidate> queue = new PriorityQueue<>();
		// 初始化队列，添加所有相邻簇对
		for (Cluster c1 : clusters) {
			for (Map.Entry<Cluster, Double> entry : c1.adjacency) {
				Cluster c2 = entry.getKey();
				if (c1.id.compareTo(c2.id) < 0) { // 避免重复添加
					queue.add(new Candidate(c1, c2, entry.getValue()));
				}
			}
		}

		while (!queue.isEmpty()) {
			Candidate candidate = queue.poll();
			Cluster c1 = candidate.c1;
			Cluster c2 = candidate.c2;
			if (!c1.alive || !c2.alive) continue;

			double mergedValue = c1.totalValue + c2.totalValue;
			if (mergedValue <= maxBlockSize) {
				c1.merge(c2, candidate.edgeSum);
				// 将新簇的邻接关系加入队列
				for (Map.Entry<Cluster, Double> entry : c1.adjacency) {
					Cluster neighbor = entry.getKey();
					queue.add(new Candidate(c1, neighbor, entry.getValue()));
				}
			}
		}

		// 收集活跃的簇
		List<Cluster> list = new ArrayList<>();
		for (Cluster c : clusters) {
			if (c.alive) list.add(c);
		}
		return list;
	}

	// 增强的后处理方法
	static void mergeTinyGlobal(List<Cluster> clusters, long blockSize, long minBlockSize, long maxBlockSize) {
		// 阶段2：全局非邻接合并
		PriorityQueue<TinyCandidate> globalQueue = new PriorityQueue<>();

		// 生成候选队列
		List<Cluster> smallClusters = new ArrayList<>();
		for (Cluster cluster : clusters) {
			if (cluster.alive && cluster.totalValue < minBlockSize) {
				smallClusters.add(cluster);
			}
		}

		// 构建全局候选对
		for (int i = 0; i < smallClusters.size(); i++) {
			for (int j = i+1; j < smallClusters.size(); j++) {
				Cluster c1 = smallClusters.get(i);
				Cluster c2 = smallClusters.get(j);
				if (c1.totalValue + c2.totalValue <= maxBlockSize) {
					globalQueue.add(new TinyCandidate(c1, c2, blockSize));
				}
			}
		}

		// 处理全局合并
		while (!globalQueue.isEmpty()) {
			TinyCandidate candidate = globalQueue.poll();
			if (!candidate.c1.alive || !candidate.c2.alive || candidate.c1.totalValue + candidate.c2.totalValue > maxBlockSize) continue;

			// 执行合并
			candidate.c1.merge(candidate.c2, candidate.adj);

			// 更新候选队列
			smallClusters.remove(candidate.c2);
			Cluster merged = candidate.c1;

			// 生成新的候选对
			for (Cluster c : smallClusters) {
				if (c != merged && (merged.totalValue + c.totalValue) <= maxBlockSize) {
					globalQueue.add(new TinyCandidate(merged, c, blockSize));
				}
			}
		}
	}

	static class Candidate implements Comparable<Candidate> {
		Cluster c1;
		Cluster c2;
		double edgeSum;

		public Candidate(Cluster c1, Cluster c2, double edgeSum) {
			this.c1 = c1;
			this.c2 = c2;
			this.edgeSum = edgeSum;
		}

		@Override
		public int compareTo(Candidate other) {
			return Double.compare(this.edgeSum, other.edgeSum);
		}
	}

	// 新增合并候选评估类
	static class TinyCandidate implements Comparable<TinyCandidate> {
		Cluster c1;
		Cluster c2;
		double valueDiff;    // 与目标值的差距
		double edgeCost;     // 合并带来的边权增量
		double adj;

		public TinyCandidate(Cluster c1, Cluster c2, double target) {
			this.c1 = c1;
			this.c2 = c2;
			this.valueDiff = Math.abs((c1.totalValue + c2.totalValue) - target);
			this.adj = c1.adjacency.getOrDefault(c2, XToDouble.default0()).value;
			this.edgeCost = c1.internalEdgeSum + c2.internalEdgeSum + adj;
		}

		@Override
		public int compareTo(TinyCandidate o) {
			// 优先考虑边权增量小的，其次考虑价值最接近目标的
			int valueCompare = Double.compare(this.edgeCost, o.edgeCost);
			return valueCompare != 0 ? valueCompare : Double.compare(this.valueDiff, o.valueDiff);
		}
	}

	static class Cluster {
		Cluster _next;

		String id;
		long totalValue;
		double internalEdgeSum;
		XashMap<Cluster, XToDouble<Cluster>> adjacency; // 邻接簇及其边权总和
		boolean alive;
		List<Cluster> combined = new SimpleList<>();

		public Cluster(String id, long size) {
			this.id = id;
			this.totalValue = size;
			this.internalEdgeSum = 0;
			this.adjacency = XToDouble.newMap();
			this.alive = true;
		}

		public void merge(Cluster other, double edgeSum) {
			this.totalValue += other.totalValue;
			this.internalEdgeSum += other.internalEdgeSum + edgeSum;
			other.alive = false;

			combined.add(other);
			combined.addAll(other.combined);

			// 合并邻接表
			for (var entry : other.adjacency) {
				Cluster neighbor = entry.getKey();
				if (neighbor == this) continue;

				double weight = entry.value;

				increment(neighbor, weight);
				neighbor.adjacency.removeKey(other);
				neighbor.increment(this, weight);
			}
			this.adjacency.removeKey(other);
		}

		public void increment(Cluster owner, double weight) {
			adjacency.computeIfAbsent(owner).value += weight;
		}

		@Override
		public String toString() {
			return id;
		}
	}
}

