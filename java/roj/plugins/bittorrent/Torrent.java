package roj.plugins.bittorrent;

import roj.concurrent.Liu;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.auto.Name;
import roj.config.auto.Optional;
import roj.io.IOUtil;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/11/29 0029 2:40
 */
@Optional
public class Torrent {
	public static Torrent read(File file) throws IOException, ParseException {return ConfigMaster.BENCODE.readObject(Torrent.class, file);}

	public String announce;
	@Name("announce-list")
	public List<String[]> announce_list;

	@Optional(Optional.Mode.NEVER)
	public Info info;

	@Name("creation date")
	public long creation_date;
	public String comment;
	@Name("created by")
	public String created_by;

	public String encoding;

	@Override
	public String toString() {
		var sb = new CharList().append("Torrent{").append(info).append(", server=[");
		if (announce_list == null) sb.append(announce).append(']');
		else sb.append(Liu.of(announce_list).map(t -> t[0]).join(", "));
		sb.append(']');
		if (creation_date != 0) {
			sb.append(", 发布于: ").append(ACalendar.toLocalTimeString(creation_date*1000));
		}
		if (comment != null) sb.append(", 发布者: ").append(comment);
		if (created_by != null) sb.append(", 制作软件: ").append(created_by);
		if (encoding != null) sb.append(", 编码: ").append(encoding);

		return sb.append('}').toStringAndFree();
	}

	@Optional
	public static final class Info {
		// BitComet
		public byte[] ed2k;
		public byte[] filehash;

		//EITHER
		public List<_File> files;
		//OR
		public long length;
		public String name;
		@Name("name.utf-8") public String nameUtf;
		@Deprecated String md5sum;

		@Name("piece length")
		@Optional(Optional.Mode.NEVER)
		public long piece_length;
		@Optional(Optional.Mode.NEVER)
		public byte[] pieces;

		@Name("private")
		@Optional(Optional.Mode.IF_EMPTY)
		public java.util.Optional<Byte> isPrivate = java.util.Optional.empty();

		public String publisher;
		@Name("publisher-url")
		public String publisherUrl;
		@Name("publisher-url.utf-8")
		public String publisherUrlUtf;
		@Name("publisher.utf-8")
		public String publisherUtf;

		transient byte[] infoHash;
		transient long realLength;

		public long getSize() {
			if (realLength == 0) {
				if (files != null) {
					for (_File file : files) {
						realLength += file.length;
					}
				} else {
					realLength = length;
				}
			}
			return realLength;
		}

		public byte[] getInfoHash() {
			if (infoHash == null) try {
				ByteList buf = IOUtil.getSharedByteBuf();
				ConfigMaster.BENCODE.writeObject(this, buf);

				var md = MessageDigest.getInstance("SHA-1");
				md.update(buf.list, 0, buf.wIndex());
				infoHash = md.digest();
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			return infoHash;
		}

		@Override
		public String toString() {
			var sb = new CharList();
			if (files == null) {
				sb.append("[{'").append(name).append('\'').append(", ").append(TextUtil.scaledNumber1024(length)).append("}]");
			} else {
				sb.append('[').append(Liu.of(files).map(x -> x, 10, Liu.terminator("...")).join(", ")).append(']');
			}
			sb.append(", [").append(pieces.length/20).append(" pieces of ").append(TextUtil.scaledNumber1024(piece_length)).append("]");
			if (isPrivate.orElse((byte) 0) != 0) sb.append(", 私有");
			return sb.toStringAndFree();
		}

		public int fileCount() {return files == null ? 1 : files.size();}
	}

	public static class _File {
		// BitComet
		@Optional public byte[] ed2k;
		@Optional public byte[] filehash;

		@Optional String name;
		@Optional @Deprecated String md5sum;

		public long length;
		public List<String> path;
		@Optional
		@Name("path.utf-8")
		public List<String> pathUtf;

		@Override
		public String toString() {
			return "{'"+TextUtil.join(path, "/")+"', "+TextUtil.scaledNumber1024(length)+'}';
		}
	}
}
