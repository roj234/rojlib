package roj.plugins.pinyin;

import org.jetbrains.annotations.NotNull;
import roj.collect.ArrayList;
import roj.collect.IntList;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <a href="https://github.com/cjinhuo/text-search-engine">基于动态规划的模糊文本匹配</a>
 * with some modifications
 * @author Roj234
 * @since 2025/07/20 21:14
 */
public class TextSearchEngine {
	public record Boundary(int start, int end) implements Comparable<Boundary> {
		public int getStart() {return start;}
		public int getEnd() {return end;}
		public String toString() {return "["+start+","+end+"]";}
		public int compareTo(@NotNull Boundary o) {return Integer.compare(start, o.start);}
	}

	public record BoundaryMapping(
			String pinyinString,
			List<Boundary> boundary,
			String originalString,
			int[] originalIndices,
			int originalLength) {
	}
	public static BoundaryMapping extractBoundaryMapping(String source) {
		// input=a测试的
		// output=[[a], [测, ce], [试, shi], [的, de, di]]
		List<String[]> choices = JPinyin.getInstance().toChoices(source);

		int index = 0;
		List<Boundary> boundaries = new java.util.ArrayList<>();
		boundaries.add(null); // 访问不到的

		IntList originalIndices = new IntList();
		CharList pinyinString = new CharList();

		for (int i = 0; i < choices.size(); i++) {
			String[] value = choices.get(i);
			pinyinString.append(value[0]);
			boundaries.add(new Boundary(i, index));
			originalIndices.add(index);
			index++;

			for (int j = 1; j < value.length; j++) {
				String pinyinItem = value[j];
				pinyinString.append(pinyinItem);
				int length = pinyinItem.length();
				Boundary boundary = new Boundary(i, index);
				for (int k = 0; k < length; k++)
					boundaries.add(boundary);
				index += length;
			}
		}

		originalIndices.add(pinyinString.length());

		return new BoundaryMapping(
				pinyinString.toStringAndFree(),
				boundaries,
				source,
				originalIndices.toArray(),
				choices.size()
		);
	}

	public record SearchResult(int score, List<Boundary> hits, BoundaryMapping data) implements Comparable<SearchResult> {
		private int hole() {
			int offset = hits.get(0).getStart();
			int hole = 0;

			for (Boundary range : hits) {
				int start = range.getStart();
				int end = range.getEnd();
				if (offset < start) {
					hole += start - offset;
				}
				offset = Math.max(offset, end + 1);
			}

			hole += hits.get(hits.size()-1).getEnd() - offset;

			return hole;
		}
		public int score() {
			var base = score;
			// 随便选的值，看起来效果尚可
			base -= (hits.size() - 1) * 20;
			base -= hole() * 5;
			base -= hits.get(0).start * 2;
			return base;
		}

		public int compareTo(@NotNull TextSearchEngine.SearchResult o) {return Integer.compare(o.score(), score());}
	}
	public SearchResult search(BoundaryMapping source, String target) {
		int startIndex = source.originalString.indexOf(target);
		if (startIndex != -1)
			return new SearchResult(
					// 随便选的值，看起来效果尚可
					target.length() * 4,
					Collections.singletonList(new Boundary(startIndex, startIndex + target.length() - 1)),
					source
			);

		totalScores = 0;
		var allHits = hitBoundaries;
		allHits.clear();

		for (String word : TextUtil.split(target, ' ')) {
			if (word.isBlank()) continue;

			hitAnyWord: {
				for (Boundary range : getRestRanges(source.originalLength, allHits)) {
					if (fuzzySearchDP(
							source,
							word,
							range.getStart(),
							range.getEnd()
					)) break hitAnyWord;
				}

				allHits.clear();
				return null;
			}
		}

		allHits.sort(null);
		return new SearchResult(totalScores, ArrayUtil.immutableCopyOf(allHits), source);
	}
	// 返回0-totalLength区间中未包含在ranges里的片段
	private static List<Boundary> getRestRanges(int totalLength, List<Boundary> ranges) {
		if (ranges.isEmpty()) return Collections.singletonList(new Boundary(0, totalLength));

		ranges.sort(null);

		List<Boundary> restRanges = new java.util.ArrayList<>();
		int offset = 0;

		for (Boundary range : ranges) {
			int start = range.getStart();
			int end = range.getEnd();
			if (offset < start) {
				restRanges.add(new Boundary(offset, start));
			}
			offset = Math.max(offset, end + 1);
		}

		if (offset < totalLength) {
			restRanges.add(new Boundary(offset, totalLength));
		}

		return restRanges;
	}

	private int[] matchPositions = ArrayCache.INTS;
	private final List<Boundary> hitBoundaries = new ArrayList<>();
	private int totalScores;

	private record DPTableItem(int matchedCharacters, int matchedLetters, int boundaryStart, int boundaryEnd) {
		static final DPTableItem NULL = new DPTableItem(0, 0, -1, -1);
	}
	private record MatchPathItem(int start, int end, int matchedLetters) {}
	// 基于动态规划的中英拼音混合模糊搜索
	private boolean fuzzySearchDP(BoundaryMapping source, String target, int startIndex, int endIndex) {
		// 获取原始索引数组和原始长度
		int[] originalIndices = source.originalIndices;
		int originalLength = source.originalLength;

		// 计算拼音的截取范围
		int pinyinStart = originalIndices[startIndex];
		int pinyinEnd = originalIndices[endIndex];
		String pinyinString = source.pinyinString.substring(pinyinStart, pinyinEnd);
		List<Boundary> boundaries = source.boundary.subList(pinyinStart, Math.min(pinyinEnd+1, source.boundary.size()));

		int targetLength = target.length();
		int pinyinLength = boundaries.size()-1;

		if (targetLength == 0 || pinyinLength < targetLength || originalLength == 0) return false;

		// 第一个有效边界
		int startBoundary = boundaries.get(1).getStart();
		int endBoundary = boundaries.get(1).getEnd();

		// 找到目标字符串在拼音字符串中的位置
		int[] matchPositions = this.matchPositions;
		if (matchPositions.length < targetLength) this.matchPositions = matchPositions = new int[targetLength];

		// 如果未能完全匹配目标字符串，返回 null
		// 注意这只是一个fail-fast检查，而不是验证，例如“湛zhanshen青”=zs青 可以通过这个检查
		{
			int j = -1;
			for (int i = 0; i < targetLength; i++) {
				j = pinyinString.indexOf(target.charAt(i), j+1);
				if (j < 0 || j > pinyinLength) return false;

				matchPositions[i] = j;
			}
		}

		DPTableItem[] dpTable = new DPTableItem[pinyinLength+1];
		Arrays.fill(dpTable, DPTableItem.NULL);
		int[] dpScores = new int[pinyinLength+1];
		MatchPathItem[][] dpMatchPath = new MatchPathItem[pinyinLength+1][targetLength];

		// 动态规划
		for (int matchIdx = 0; matchIdx < targetLength; matchIdx++) {
			int matchedPinyinIndex = matchPositions[matchIdx];

			DPTableItem prevDpTableItem = dpTable[matchedPinyinIndex];
			int prevScore = dpScores[matchedPinyinIndex];

			dpScores[matchedPinyinIndex] = 0;
			dpTable[matchedPinyinIndex] = DPTableItem.NULL;

			// 标记是否找到当前字符的有效匹配
			var foundValidMatchForCurrentChar = false;
			for (int j = matchedPinyinIndex + 1; j <= pinyinLength; j++) {
				int tempScore = dpScores[j];

				// 获取当前边界信息
				Boundary currentBoundary = boundaries.get(j);
				int boundaryStartOffset = currentBoundary.getStart() - startBoundary;
				int boundaryEndOffset = currentBoundary.getEnd() - endBoundary;

				// 检查是否是新词（新汉字开始）
				boolean isNewWord = (j - 1) == (currentBoundary.getEnd() - endBoundary) &&
						prevDpTableItem.boundaryStart != boundaryStartOffset;

				// 检查是否是连续匹配
				boolean isContinuation = false;
				if (prevDpTableItem.matchedCharacters > 0) {
					if (prevDpTableItem.boundaryEnd == boundaryEndOffset) {
						// 检查前一个字符是否匹配
						if (matchIdx > 0 && j >= 2) {
							char prevChar = pinyinString.charAt(j - 2);
							if (prevChar == target.charAt(matchIdx - 1)) {
								isContinuation = true;
							}
						}
					}
				}

				// 检查当前字符是否匹配
				boolean isEqual = (j <= pinyinLength) &&
						(pinyinString.charAt(j - 1) == target.charAt(matchIdx));

				// 如果满足匹配条件，更新状态
				if (isEqual && (isNewWord || isContinuation) && (matchIdx == 0 || prevScore > 0)) {
					int newScore = prevScore + prevDpTableItem.matchedLetters * 2 + 1;
					int newMatchedLetters = prevDpTableItem.matchedLetters + 1;
					int newMatchedCharacters = prevDpTableItem.matchedCharacters + (isNewWord ? 1 : 0);

					if (newScore >= dpScores[j]) {
						dpScores[j] = newScore;
						dpTable[j] = new DPTableItem(
								newMatchedCharacters,
								newMatchedLetters,
								boundaryStartOffset,
								boundaryEndOffset
						);

						boolean newMatched = newScore > tempScore;
						if (newMatched) {
							int originalStringIndex = currentBoundary.getStart() - startBoundary;
							int startOriginal = originalStringIndex - prevDpTableItem.matchedCharacters + (isNewWord ? 0 : 1);
							dpMatchPath[j][matchIdx] = new MatchPathItem(
									startOriginal,
									originalStringIndex,
									newMatchedLetters
							);
						} else {
							dpMatchPath[j][matchIdx] = dpMatchPath[j-1][matchIdx];
						}

						// 当前字符遍历一遍，如果都没有进入当前 if 分支说明没有匹配到，在外层即可 return
						// issue: https://github.com/cjinhuo/text-search-engine/issues/21
						foundValidMatchForCurrentChar = true;
						continue;
					}
				}

				// 如果不满足匹配条件，继承前一个状态
				dpScores[j] = dpScores[j - 1];
				dpMatchPath[j][matchIdx] = dpMatchPath[j - 1][matchIdx];

				// 计算字符间隔
				int gap = boundaries.get(j).getStart() - startBoundary - dpTable[j - 1].boundaryStart;

				// 检查是否在同一个词内
				boolean isSameWord = false;
				if (j < pinyinLength) {
					Boundary nextBoundary = boundaries.get(j + 1);
					isSameWord = currentBoundary.getStart() == nextBoundary.getStart();
				}

				// 更新DP表
				if (gap == 0 || (j < pinyinLength && gap == 1 && isSameWord)) {
					dpTable[j] = dpTable[j - 1];
				} else {
					dpTable[j] = DPTableItem.NULL;
				}
			}

			if (!foundValidMatchForCurrentChar) return false;
		}

		// 步骤4: 回溯查找匹配范围
		if (dpMatchPath[pinyinLength][targetLength - 1] == null) return false;

		int scores = 0;

		int gIndex = pinyinLength;
		int restMatched = targetLength - 1;

		while (restMatched >= 0) {
			MatchPathItem item = dpMatchPath[gIndex][restMatched];
			if (item == null) break;

			scores += dpScores[gIndex];

			int start = item.start + startIndex;
			int end = item.end + startIndex;
			hitBoundaries.add(new Boundary(start, end)); // 逆序，不过最终要sort的

			// 更新回溯索引
			int pinyinPos = originalIndices[start] - pinyinStart;
			gIndex = pinyinPos - 1;

			restMatched -= item.matchedLetters;
		}

		this.totalScores += scores;
		return true;
	}
}