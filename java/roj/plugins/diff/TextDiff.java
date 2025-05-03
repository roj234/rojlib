package roj.plugins.diff;

import org.intellij.lang.annotations.MagicConstant;
import roj.config.serial.CVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本差异对象
 * @author Roj234
 * @since 2024/3/5 2:49
 */
public class TextDiff {
	// 定义补丁类型
	interface Patch { }

	static class Copy implements Patch {
		final int start, count;

		Copy(int start, int count) {
			this.start = start;
			this.count = count;
		}

		@Override
		public String toString() {return "copy { "+start+","+count+" }";}
	}
	static class Append implements Patch {
		final char[] data;
		Append(char[] data) {this.data = data;}
		@Override
		public String toString() {return "append { "+data+" }";}
	}

	// 定义差异类型
	public static final byte EQUAL = 0, REPLACE = 1, INSERT = 2, DELETE = 3;
	public static final String[] TYPES = {"EQUAL","REPLACE","INSERT","DELETE"};

	final byte type;
	final int[] ranges;

	public TextDiff(@MagicConstant(intValues = {EQUAL, REPLACE, INSERT, DELETE}) byte type, int originalStart, int originalEnd, int modifiedStart, int modifiedEnd) {
		this.type = type;
		this.ranges = new int[]{ originalStart, originalEnd, modifiedStart, modifiedEnd };
	}

	@MagicConstant(intValues = {EQUAL, REPLACE, INSERT, DELETE})
	public byte getType() {return type;}
	public int[] getRanges() {return ranges;}

	public void accept(CVisitor cv) {
		cv.valueMap(3);
		cv.key("type");
		cv.value(TYPES[type]);
		cv.key("originalRange");
		cv.valueList(2);
		cv.value(ranges[0]);
		cv.value(ranges[1]);
		cv.pop();
		cv.key("modifiedRange");
		cv.valueList(2);
		cv.value(ranges[2]);
		cv.value(ranges[3]);
		cv.pop();
	}

	public void acceptSchemaless(CVisitor cv) {
		cv.valueMap(3);
		cv.intKey(0);
		cv.value(TYPES[type]);
		cv.intKey(1);
		cv.valueList(2);
		cv.value(ranges[0]);
		cv.value(ranges[1]);
		cv.pop();
		cv.intKey(2);
		cv.valueList(2);
		cv.value(ranges[2]);
		cv.value(ranges[3]);
		cv.pop();
	}

	@Override
	public String toString() {
		return type+"{originalRange=["+ranges[0]+", "+ranges[1]+"], modifiedRange=["+ranges[2]+", "+ranges[3]+"]}";
	}

	public static void main(String[] args) {
		var original = "快速棕毛狐狸了懒狗";
		//[
		//	append { "敏捷的" },
		//	copy { 2, 4 }, // "棕毛狐狸"
		//	append { "跃过" },
		//	copy { 6, 2 } // "了懒"
		//]
		System.out.println(generateDiff(original.toCharArray(), new Patch[]{
				new Append("敏捷的".toCharArray()),
				new Copy(2, 4),
				new Append("跃过".toCharArray()),
				new Copy(6, 2)
		}));
	}

	public static List<TextDiff> generateDiff(char[] original, Patch[] patches) {
		List<TextDiff> diffs = new ArrayList<>();
		int origPos = 0, modPos = 0;
		int pendingInsertStart = -1, pendingInsertEnd = -1;

		for (Patch patch : patches) {
			if (patch instanceof Append append) {
				if (pendingInsertStart == -1) {
					pendingInsertStart = modPos;
					pendingInsertEnd = modPos + append.data.length;
				} else {
					pendingInsertEnd += append.data.length;
				}
				modPos += append.data.length;
			} else if (patch instanceof Copy copy) {
				// 处理挂起的插入
				if (pendingInsertStart != -1) {
					if (origPos < copy.start) {
						// 替换
						diffs.add(new TextDiff(
								REPLACE,
								origPos, copy.start,
								pendingInsertStart, pendingInsertEnd
						));
						origPos = copy.start;
					} else {
						// 插入
						diffs.add(new TextDiff(
								INSERT,
								origPos, origPos,
								pendingInsertStart, pendingInsertEnd
						));
					}
					pendingInsertStart = -1;
					pendingInsertEnd = -1;
				}
				// 处理origPos < copy.start的情况（删除）
				if (origPos < copy.start) {
					diffs.add(new TextDiff(
							DELETE,
							origPos, copy.start,
							modPos, modPos
					));
					origPos = copy.start;
				}
				// 处理copy操作
				int origEnd = copy.start + copy.count;
				int modEnd = modPos + copy.count;
				diffs.add(new TextDiff(
						EQUAL,
						copy.start, origEnd,
						modPos, modEnd
				));
				origPos = origEnd;
				modPos = modEnd;
			}
		}

		// 处理剩余的挂起插入
		if (pendingInsertStart != -1) {
			if (origPos < original.length) {
				// 替换
				diffs.add(new TextDiff(
						REPLACE,
						origPos, original.length,
						pendingInsertStart, pendingInsertEnd
				));
				origPos = original.length;
			} else {
				// 插入
				diffs.add(new TextDiff(
						INSERT,
						origPos, origPos,
						pendingInsertStart, pendingInsertEnd
				));
			}
		}

		// 处理剩余的original部分
		if (origPos < original.length) {
			diffs.add(new TextDiff(
					DELETE,
					origPos, original.length,
					modPos, modPos
			));
		}

		return diffs;
	}
}