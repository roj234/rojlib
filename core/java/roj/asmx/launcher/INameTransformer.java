package roj.asmx.launcher;

/**
 * @author Roj234
 * @since 2023/8/4 16:47
 */
public interface INameTransformer {
	String mapName(String name);
	String unmapName(String name);
}