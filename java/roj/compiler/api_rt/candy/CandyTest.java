package roj.compiler.api_rt.candy;

/**
 * @author Roj234
 * @since 2024/2/20 0020 1:28
 */
public class CandyTest {
	public static int compareTest() {
		return ComparisonChain.start().compare(1, 2).compare("asdf12312", "dfbshdf").compare(222f, 333d).result();
	}
}