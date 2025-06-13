package roj.audio;

import org.jetbrains.annotations.Nullable;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

/**
 * @author Roj234
 * @since 2024/2/19 1:20
 */
public interface AudioMetadata {
	String toString();
	/**
	 * 获取歌曲标题。
	 * @return 歌曲标题
	 */
	@Nullable String getTitle();
	/**
	 * 获取歌曲艺术家。
	 * @return 歌曲艺术家
	 */
	@Nullable String getArtist();
	/**
	 * 获取歌曲唱片集。
	 * @return 歌曲唱片集
	 */
	@Nullable String getAlbum();
	/**
	 * 获取歌曲发行年份。
	 * @return 歌曲发行年份
	 */
	@Nullable default String getYear() { return null; }
	/**
	 * 获取歌曲编码软件。
	 * @return 歌曲编码软件
	 */
	@Nullable default String getCoder() { return null; }
	/**
	 * 获取歌词。
	 * @return 歌词
	 */
	@Nullable default String getLyrics() {return null;}

	/**
	 * 获取文件中内置的唱片集图片的mime类型
	 */
	@Nullable default String getPictureMime() { return null; }
	/**
	 * 获取文件中内置的唱片集图片
	 * @param buf 用于放置数据
	 */
	default void getPicture(DynByteBuf buf) {}

	/**
	 * 获取具名属性，不同元数据的名称大概是不会相同的
	 * @param key vendor specific
	 */
	@Nullable default <T> T getAttribute(TypedKey<T> key) { return null; }
}