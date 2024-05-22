package roj.compiler.doc;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/7/4 0004 3:03
 */
public class Javadoc {
	List<Object> text;
	List<List<Object>> blockTag;


	public void visitText(CharSequence text) {}
	/**
	 * 这部分是begin之后调用该方法之前对visitText的调用
	 * 除此之外{@sometag abc}这种内联标签是需要Visitor解析
	 * @param tag 这部分是调用该方法之后对visitText的调用
	 * 这部分实际上包含了 tag 空格 其他内容 以及这一段话
	 */
	public void visitBlockTag(String tag) {}
	public void visitEnd() {}
}