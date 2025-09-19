import *

/**
 * Lava Compiler - 类结构Parser<p>
 * Parser levels: <ol>
 *     <li><b><i>Class Parser</i></b></li>
 *     <li>{@link ParseTask Segment Parser}</li>
 * </ol>
 * @author solo6975
 * @since 2020/12/31 17:34
 */
public class Test {
	/**
	 * 这部分是begin之后调用该方法之前对visitText的调用
	 * 除此之外{@sometag abc}这种内联标签是需要Visitor解析
	 * @param args 这部分是调用该方法之后对visitText的调用
	 * 这部分实际上包含了 args 空格 其他内容 以及这一段话
	 */
	public static void main(String[] args) {
	}
}
