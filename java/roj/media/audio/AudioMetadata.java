package roj.media.audio;

import org.jetbrains.annotations.Nullable;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/2/19 0019 1:20
 */
public interface AudioMetadata {
	String toString();

	/**
	 * 获取歌曲标题。
	 * @return 歌曲标题
	 */
	@Nullable
	String getTitle();

	/**
	 * 获取歌曲艺术家。
	 * @return 歌曲艺术家
	 */
	@Nullable
	String getArtist();

	/**
	 * 获取歌曲唱片集。
	 * @return 歌曲唱片集
	 */
	@Nullable
	String getAlbum();

	/**
	 * 获取歌曲发行年份。
	 * @return 歌曲发行年份
	 */
	@Nullable
	default String getYear() { return null; }
	/**
	 * 获取歌曲编码软件。
	 * @return 歌曲编码软件
	 */
	@Nullable
	default String getCoder() { return null; }

	default boolean hasPicture() { return false; }
	/**
	 * 获取文件中内置的唱片集图片
	 */
	default void getPicture(DynByteBuf buf) {}

	@Nullable
	default String getNamedAttribute(String name) { return null; }
}