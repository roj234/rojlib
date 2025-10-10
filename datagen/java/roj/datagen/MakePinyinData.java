package roj.datagen;

import roj.archive.xz.LZMA2Options;
import roj.archive.xz.LZMA2OutputStream;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.*;
import roj.config.JsonParser;
import roj.config.node.MapValue;
import roj.http.HttpRequest;
import roj.http.HttpResponse;
import roj.http.curl.DownloadListener;
import roj.http.curl.DownloadTask;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.text.*;
import roj.ui.EasyProgressBar;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/12/22 16:31
 */
public class MakePinyinData {
	public static void main(String[] args) throws Exception {
		var input = new File(".");
		var output = new File("RojLib/ext/pinyin/resources");

		// Last Update: v0.15.0
		var wordPinyinFile = getLatestFile(input, "mozillazg/pinyin-data", "pinyin.txt");

		// Last Update: v0.19.0
		var phrasePinyinFile = getLatestFile(input, "mozillazg/phrase-pinyin-data", "large_pinyin.txt");

		File outputFile = new File(output, "roj/text/pinyin/JPinyin.lzma");
		processPinyinData(wordPinyinFile, phrasePinyinFile, outputFile);
	}

	private static File getLatestFile(File downloadDir, String repo, String file) throws Exception {
		DateFormat dateFormat = DateFormat.create("YYYY-MM-DD\"T\"HH:ii:ss\"Z\"");

		File localFile = new File(downloadDir, file);
		//long updateCheckPeriod = System.currentTimeMillis() - 86400000 * 7;
		//if (localFile.lastModified() > updateCheckPeriod) return localFile;

		HttpResponse client = HttpRequest.builder().GET().uri("https://api.github.com/repos/"+repo+"/releases/latest").executePooled();
		MapValue responseJson = new JsonParser().parse(client.text()).asMap();
		if (responseJson.containsKey("message")) {
			System.out.println("Github API failed: "+responseJson.getString("message"));
			return localFile;
		}

		long time = dateFormat.parse(new CharList(responseJson.getString("updated_at")));

		if (localFile.lastModified() < time) {
			System.out.println("Update local file: "+localFile+", remote version: "+responseJson.getString("tag_name"));

			File newFile = new File(localFile.getAbsolutePath() + ".latest");
			DownloadListener listener = new DownloadListener.Single();
			var task = DownloadTask.createTask("https://github.com/"+repo+"/raw/refs/heads/master/"+file, newFile, listener);
			task.chunkStart = Integer.MAX_VALUE;
			task.run();
			task.get();

			localFile.delete();
			newFile.renameTo(localFile);
			localFile.setLastModified(time);
		}

		return localFile;
	}

	/**
	 * 处理拼音数据：读取单字和词组拼音文件，构建拼音映射，优化存储，并输出压缩文件。
	 *
	 * @param wordPinyinFile 单字拼音数据文件
	 * @param phrasePinyinFile 词组拼音数据文件
	 * @param outputFile 输出压缩文件
	 * @throws Exception 如果文件读取或处理失败
	 */
	private static void processPinyinData(File wordPinyinFile, File phrasePinyinFile, File outputFile) throws Exception {
		TrieTree<List<String>> pinyinTrie = new TrieTree<>();
		EasyProgressBar progressBar = new EasyProgressBar();

		// 分析拼音数据并填充拼音Trie
		analyzePinyinData(wordPinyinFile, phrasePinyinFile, progressBar, pinyinTrie);
		CharList sharedCharBuffer = IOUtil.getSharedCharBuf();

		// 统计声母和韵母的频率
		ToIntMap<String> firstFrequencyMap = new ToIntMap<>();
		ToIntMap<String> lastFrequencyMap = new ToIntMap<>();
		for (List<String> pinyinList : pinyinTrie.values()) {
			for (String pinyinStr : pinyinList) {
				sharedCharBuffer.clear();
				String[] splitPinyin = splitPinyin(removeTone(sharedCharBuffer.append(pinyinStr)));
				firstFrequencyMap.increment(splitPinyin[0], 1);
				lastFrequencyMap.increment(splitPinyin[1], 1);
			}
		}

		// 确保声母和韵母数量在255以内（用于字节存储）
		if (firstFrequencyMap.size() > 255) throw new AssertionError();
		if (lastFrequencyMap.size() > 255) throw new AssertionError();

		firstFrequencyMap.remove("");
		lastFrequencyMap.remove("");

		// 根据频率排序声母和韵母，并分配索引（频率高的索引小）
		List<ToIntMap.Entry<String>> initialEntries = new ArrayList<>(firstFrequencyMap.selfEntrySet());
		initialEntries.sort(ToIntMap.Entry.reverseComparator());
		for (int i = 0; i < initialEntries.size(); i++) initialEntries.get(i).value = i + 1;

		List<ToIntMap.Entry<String>> finalEntries = new ArrayList<>(lastFrequencyMap.selfEntrySet());
		finalEntries.sort(ToIntMap.Entry.reverseComparator());
		for (int i = 0; i < finalEntries.size(); i++) finalEntries.get(i).value = i + 1;

		// 获取所有拼音条目并按长度排序（最长匹配优先）
		ArrayList<Map.Entry<CharSequence, List<String>>> pinyinEntries = new ArrayList<>(pinyinTrie.entrySet());
		pinyinEntries.sort((entry1, entry2) -> {
			int lengthCompare = Long.compare(entry2.getKey().codePoints().count(), entry1.getKey().codePoints().count());
			return lengthCompare != 0 ? lengthCompare : entry1.getKey().toString().compareTo(entry2.getKey().toString());
		});

		int toneSpaceSaved = 0;
		progressBar.setTotal(pinyinEntries.size());
		IntList offsetList = new IntList();

		ByteList tonePool = new ByteList(); // 存储所有拼音声母韵母序列
		ByteList toneBytes = new ByteList();

		for (var entry : pinyinEntries) {
			toneBytes.clear();
			List<String> pinyinList = entry.getValue();
			for (String pinyinStr : pinyinList) {
				sharedCharBuffer.clear();
				String[] splitPinyin = splitPinyin(removeTone(sharedCharBuffer.append(pinyinStr)));
				toneBytes.put(firstFrequencyMap.getOrDefault(splitPinyin[0], 0));
				toneBytes.put(lastFrequencyMap.getOrDefault(splitPinyin[1], 0));
			}

			int toneOffset = TextUtil.indexOf(tonePool, toneBytes, 0, tonePool.wIndex() - toneBytes.wIndex());
			if (toneOffset < 0) {
				toneOffset = tonePool.length();
				tonePool.put(toneBytes);
			} else {
				toneSpaceSaved += toneBytes.length();
			}

			offsetList.add(toneOffset);
			offsetList.add(toneBytes.length());

			progressBar.increment();
		}
		progressBar.end();
		progressBar.reset();

		// 计算文本和音调长度的比特数
		int toneBits = Integer.numberOfTrailingZeros(MathUtils.nextPowerOfTwo(tonePool.length()));
		System.out.println("TonePool=" + tonePool.length()+", toneSpaceSaved="+toneSpaceSaved);
		System.out.println("ToneBits: " + toneBits);

		// 配置LZMA压缩选项
		LZMA2Options lzmaOptions = new LZMA2Options(9).setDictSize(65536+32768);

		ByteList byteBuffer = new ByteList();
		try (OutputStream outputStream = lzmaOptions.getOutputStream(new FileOutputStream(outputFile))) {
			LZMA2OutputStream lzmaWriter = (LZMA2OutputStream) outputStream;

			byteBuffer.clear();
			byteBuffer.put(initialEntries.size()).put(finalEntries.size());
			for (ToIntMap.Entry<String> initialEntry : initialEntries) {
				byteBuffer.putVUIStr(initialEntry.key, FastCharset.UTF16BE());
			}
			for (ToIntMap.Entry<String> finalEntry : finalEntries) {
				byteBuffer.putVUIStr(finalEntry.key, FastCharset.UTF16BE());
			}

			byteBuffer.putInt(tonePool.length()).writeAlignment(4).put(tonePool);

			int textPoolCompressedSize = lzmaOptions.findBestProps(byteBuffer.toByteArray());
			lzmaWriter.setProps(lzmaOptions);
			byteBuffer.writeToStream(outputStream);
			System.out.println("Header+Constants: "+lzmaOptions+", "+byteBuffer.length()+" => "+textPoolCompressedSize);

			// 写入偏移量数组
			byteBuffer.clear();
			byteBuffer.putInt(pinyinEntries.size()).putInt(toneBits);

			int offsetIndex = 0;
			for (var entry : pinyinEntries) {
				byteBuffer.putVUIStr(entry.getKey(), FastCharset.UTF16BE());

				int toneOffset = offsetList.get(offsetIndex++);
				int toneLength = offsetList.get(offsetIndex++);
				byteBuffer.putInt((toneLength << toneBits) | toneOffset);

				byteBuffer.writeAlignment(4);
			}

			int offsetArrayCompressedSize = lzmaOptions.findBestProps(byteBuffer.toByteArray());
			lzmaWriter.setProps(lzmaOptions);
			byteBuffer.writeToStream(outputStream);
			System.out.println("OffsetArray: " + lzmaOptions + ", " + byteBuffer.length() + " => " + offsetArrayCompressedSize);

		}
		System.out.println("total size is "+outputFile.length());
	}

	/**
	 * 分析拼音数据文件，构建拼音映射Trie，并处理词组拼音数据。
	 *
	 * @param wordPinyinFile 单字拼音数据文件
	 * @param phrasePinyinFile 词组拼音数据文件
	 * @param progressBar 进度条用于显示进度
	 * @param pinyinTrie 拼音Trie，用于存储汉字到拼音列表的映射
	 * @throws IOException 如果文件读取失败
	 */
	private static void analyzePinyinData(File wordPinyinFile, File phrasePinyinFile, EasyProgressBar progressBar, TrieTree<List<String>> pinyinTrie) throws IOException {
		IntMap<ToIntMap<String>> pinyinUsageMap = new IntMap<>(); // 存储每个汉字的拼音使用频率

		progressBar.setName("Phase 1: analyze");
		progressBar.setUnlimited();
		// 处理单字拼音文件
		try (TextReader reader = TextReader.auto(wordPinyinFile)) {
			CharList buffer = IOUtil.getSharedCharBuf();
			Pattern pattern = Pattern.compile("^U\\+([0-9A-F]+): (.+)  #");
			Matcher matcher = pattern.matcher("");
			while (reader.readLine(buffer)) {
				if (buffer.startsWith("#")) continue;

				boolean matches = matcher.reset(buffer).find();
				assert matches;

				int codePoint = Integer.parseInt(matcher.group(1), 16);
				List<String> pinyinList = TextUtil.split(matcher.group(2), ',');

				buffer.clear();
				String chineseChar = buffer.appendCodePoint(codePoint).toString();
				pinyinTrie.put(chineseChar, pinyinList);

				ToIntMap<String> frequencyMap = new ToIntMap<>();
				pinyinUsageMap.put(codePoint, frequencyMap);

				progressBar.increment();
			}
		}

		// 处理词组拼音文件
		try (TextReader reader = TextReader.auto(phrasePinyinFile)) {
			CharList buffer = IOUtil.getSharedCharBuf();
			while (reader.readLine(buffer)) {
				if (buffer.startsWith("#")) continue;

				int separatorPos = buffer.indexOf(": ");
				String chinesePhrase = buffer.substring(0, separatorPos);
				List<String> pinyinList = TextUtil.split(buffer.subSequence(separatorPos + 2, buffer.length()), ' ');

				PrimitiveIterator.OfInt codePointIterator = chinesePhrase.codePoints().iterator();
				int pinyinIndex = 0;
				while (codePointIterator.hasNext()) {
					int codePoint = codePointIterator.nextInt();
					buffer.clear();
					String chineseChar = buffer.appendCodePoint(codePoint).toString();
					List<String> existingPinyinList = pinyinTrie.get(chineseChar);
					if (existingPinyinList == null) {
						System.err.println("缺少单字 U+" + Integer.toHexString(codePoint) + " 的拼音, 使用来自词组的 " + pinyinList.get(pinyinIndex));
						existingPinyinList = new ArrayList<>();
						pinyinTrie.put(chineseChar, existingPinyinList);
						pinyinUsageMap.put(codePoint, new ToIntMap<>());
					}

					pinyinUsageMap.get(codePoint).increment(pinyinList.get(pinyinIndex), 1);

					pinyinIndex++;
				}
				progressBar.increment();
			}
		}
		progressBar.end("总大小: " + pinyinTrie.size());
		progressBar.setTitle("Phase 2: sort");

		// 根据使用频率调整单字拼音的顺序
		for (var iterator = pinyinUsageMap.selfEntrySet().iterator(); iterator.hasNext(); ) {
			IntMap.Entry<ToIntMap<String>> entry = iterator.next();
			ToIntMap<String> frequencyMap = entry.getValue();
			if (frequencyMap.isEmpty()) {
				iterator.remove();
			} else {
				String chineseChar = new CharList().appendCodePoint(entry.getIntKey()).toString();
				List<String> sortedPinyins = pinyinTrie.get(chineseChar);
				Set<String> remain = new HashSet<>(sortedPinyins);
				sortedPinyins.clear();

				List<ToIntMap.Entry<String>> frequencyEntries = new ArrayList<>(frequencyMap.selfEntrySet());
				frequencyEntries.sort(ToIntMap.Entry.reverseComparator());

				for (ToIntMap.Entry<String> freqEntry : frequencyEntries) {
					remain.remove(freqEntry.key);
					sortedPinyins.add(freqEntry.key);
				}
				sortedPinyins.addAll(remain);
			}
		}

		// 再次处理词组文件，添加词组拼音（如果读音与单字默认读音不同）
		try (TextReader reader = TextReader.auto(phrasePinyinFile)) {
			CharList buffer = IOUtil.getSharedCharBuf();
			while (reader.readLine(buffer)) {
				if (buffer.startsWith("#")) continue;

				int separatorPos = buffer.indexOf(": ");
				String chinesePhrase = buffer.substring(0, separatorPos);
				List<String> pinyinList = TextUtil.split(buffer.subSequence(separatorPos + 2, buffer.length()), ' ');

				if (pinyinTrie.containsKey(chinesePhrase)) {
					System.out.println("词组'" + chinesePhrase + "'具有多个音 " + pinyinList + " and " + pinyinTrie.get(chinesePhrase) + "，这是暂不支持的");
					continue;
				}

				PrimitiveIterator.OfInt codePointIterator = chinesePhrase.codePoints().iterator();
				int pinyinIndex = 0;
				while (codePointIterator.hasNext()) {
					int codePoint = codePointIterator.nextInt();
					buffer.clear();
					String chineseChar = buffer.appendCodePoint(codePoint).toString();
					List<String> charPinyinList = pinyinTrie.get(chineseChar);

					if (!charPinyinList.get(0).equals(pinyinList.get(pinyinIndex))) {
						pinyinTrie.put(chinesePhrase, pinyinList);
						break; // 只要有一个字不同，就添加整个词组
					}
					pinyinIndex++;
				}
				progressBar.increment();
			}
		}
		progressBar.end("总大小: " + pinyinTrie.size());
		progressBar.setTitle("Phase 3: partialMatch");

		// 检查词组拼音是否可以被部分匹配优化
		ArrayList<Map.Entry<CharSequence, List<String>>> allEntries = new ArrayList<>(pinyinTrie.entrySet());
		allEntries.sort((entry1, entry2) -> Integer.compare(entry2.getKey().length(), entry1.getKey().length()));

		outerLoop:
		for (var entry : allEntries) {
			List<String> pinyinList = entry.getValue();
			PrimitiveIterator.OfInt codePointIterator = entry.getKey().codePoints().iterator();
			CharList buffer = IOUtil.getSharedCharBuf();

			int index = 0;
			int startDiff = -1, endDiff = -1;
			while (codePointIterator.hasNext()) {
				int codePoint = codePointIterator.nextInt();
				buffer.clear();
				String chineseChar = buffer.appendCodePoint(codePoint).toString();
				if (!pinyinTrie.get(chineseChar).get(0).equals(pinyinList.get(index))) {
					if (startDiff == -1) startDiff = index;
					endDiff = index + 1;
				}
				index++;
			}
			if (endDiff == startDiff && entry.getKey().codePoints().count() > 1) {
				System.out.println("full same " + entry.getKey());
			}
			if (endDiff - startDiff <= 1) continue;
			if (startDiff == 0 && endDiff == entry.getKey().codePoints().count()) continue;

			int[] diffCodePoints = entry.getKey().codePoints().skip(startDiff).limit(endDiff - startDiff).toArray();
			buffer.clear();
			for (int cp : diffCodePoints) buffer.appendCodePoint(cp);

			var partialEntry = pinyinTrie.get(buffer);
			if (partialEntry != null) {
				for (int j = startDiff; j < endDiff; j++) {
					if (!partialEntry.get(j - startDiff).equals(pinyinList.get(j))) {
						continue outerLoop;
					}
				}
				System.out.println("词组 " + entry.getKey() + pinyinList + "的特殊拼音[" + startDiff + "," + endDiff + "] 能在 " + buffer + partialEntry + "中找到");
				pinyinTrie.remove(entry.getKey());
			}
		}
		progressBar.end("总大小: " + pinyinTrie.size());
	}

	// 声母列表，用于分割拼音
	private static final List<String> INITIALS = Arrays.asList("zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w");

	/**
	 * 将拼音字符串分割为声母和韵母部分。
	 *
	 * @param pinyin 拼音字符串（不带音调）
	 * @return 字符串数组，第一个元素是声母，第二个元素是韵母
	 */
	public static String[] splitPinyin(CharList pinyin) {
		if (pinyin.charAt(pinyin.length()-1) <= '9') {
			for (String initial : INITIALS) {
				if (pinyin.startsWith(initial)) {
					String finalPart = pinyin.substring(initial.length());
					return new String[]{initial, finalPart};
				}
			}
		}
		return new String[]{"", pinyin.toString()};
	}

	// 正则表达式模式用于匹配不同音调的音符
	private static final Pattern FOURTH_TONE_PATTERN = Pattern.compile("[àèìòùǜǹ]");
	private static final Pattern THIRD_TONE_PATTERN = Pattern.compile("[ǎăĕěĭǐŏǒŭǔǚň]");
	private static final Pattern SECOND_TONE_PATTERN = Pattern.compile("[áéíóúǘń]");
	private static final Pattern FIRST_TONE_PATTERN = Pattern.compile("[āēīōūǖ]");

	/**
	 * 移除拼音中的音调符号，并添加音调数字（0-4）。
	 *
	 * @param buffer 包含拼音的CharList，会被修改
	 * @return 修改后的CharList，末尾添加了音调数字
	 */
	private static CharList removeTone(CharList buffer) {
		buffer.replace('ü', 'v'); // 将ü替换为v
		int replaceCount;

		// 检查并替换第四声音调
		replaceCount = buffer.preg_replace_callback(FOURTH_TONE_PATTERN, matcher -> {
			int index = matcher.pattern().pattern().indexOf(matcher.group(0));
			return String.valueOf("[aeiouvn]".charAt(index));
		});
		if (replaceCount != 0) {
			buffer.append('4');
			return buffer;
		}

		// 检查并替换第三声音调
		replaceCount = buffer.preg_replace_callback(THIRD_TONE_PATTERN, matcher -> {
			int index = matcher.pattern().pattern().indexOf(matcher.group(0));
			return String.valueOf("[aaeeiioouuvn]".charAt(index));
		});
		if (replaceCount != 0) {
			buffer.append('3');
			return buffer;
		}

		// 检查并替换第二声音调
		replaceCount = buffer.preg_replace_callback(SECOND_TONE_PATTERN, matcher -> {
			int index = matcher.pattern().pattern().indexOf(matcher.group(0));
			return String.valueOf("[aeiouvn]".charAt(index));
		});
		if (replaceCount != 0) {
			buffer.append('2');
			return buffer;
		}

		// 检查并替换第一声音调
		replaceCount = buffer.preg_replace_callback(FIRST_TONE_PATTERN, matcher -> {
			int index = matcher.pattern().pattern().indexOf(matcher.group(0));
			return String.valueOf("[aeiouv]".charAt(index));
		});
		if (replaceCount != 0) {
			buffer.append('1');
			return buffer;
		}

		// 如果没有音调，添加0，并检查是否有未处理的字符
		for (int i = 0; i < buffer.length(); i++) {
			if (buffer.list[i] > 127) {
				System.err.println("Unprocessable U+" + Integer.toHexString(buffer.list[i]));
				return buffer;
			}
		}
		buffer.append('0');
		return buffer;
	}
}