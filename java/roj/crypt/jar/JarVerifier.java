package roj.crypt.jar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFile;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.net.mss.X509CertFormat;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.URL;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Roj234
 * @since 2024/3/22 0022 19:44
 */
public class JarVerifier {
	private static final String CREATED_BY = "ImpLib/JarSigner (v1.3)";
	private static final MyHashSet<String> VALID_CERTIFICATE_EXTENSION = new MyHashSet<>("rsa", "dsa", "ec");
	private static final List<String> SECURE_HASH_ALGORITHMS = Arrays.asList("SHA-512", "SHA-384", "SHA-256");
	private static final ThreadLocal<Map<String, MessageDigest>> DIGESTS = new ThreadLocal<>();

	private final URL base;
	private Manifest manifest;

	private final SignatureBlock block;
	private transient ManifestBytes manifestBytes;
	private transient byte[] signatureBytes;

	private Map<String, String> hashes;
	private String algorithm, prevName;
	private CodeSource source;

	JarVerifier(URL url, Manifest manifest, ManifestBytes manifestBytes, byte[] signatureBytes, SignatureBlock block) {
		this.base = url;
		this.manifest = manifest;
		this.manifestBytes = manifestBytes;
		this.signatureBytes = signatureBytes;
		this.block = block;
	}

	public boolean isSigned() { return block != null; }
	public boolean isSignTrusted() {
		List<X509Certificate> certs = block.getCertificates();
		try {
			X509TrustManager tm = X509CertFormat.getDefault();
			tm.checkServerTrusted(certs.toArray(new X509Certificate[0]), "UNKNOWN");
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void ensureManifestValid(boolean lenient) throws GeneralSecurityException {
		if (source != null) return;
		if (block == null) {
			source = new CodeSource(base, (CodeSigner[]) null);
			return;
		}

		Map<String, MessageDigest> digests = DIGESTS.get();
		if (digests == null) digests = new MyHashMap<>();

		try {
			readManifestAndHash(new ManifestBytes(signatureBytes), lenient);
		} catch (IOException e) {
			throw new SecurityException("签名清单格式错误");
		}

		int flag = 0;
		Attributes main = manifest.getMainAttributes();
		for (int i = 0; i < SECURE_HASH_ALGORITHMS.size(); i++) {
			algorithm = SECURE_HASH_ALGORITHMS.get(i);
			String digestStr = main.getValue(algorithm+"-Digest-Manifest");
			if (digestStr != null && (flag & 1) == 0) {
				flag |= 1;

				MessageDigest digest = digester(digests, algorithm);
				digest.update(manifestBytes.data);

				byte[] except = Base64.decode(digestStr, IOUtil.getSharedByteBuf()).toByteArray();
				byte[] computed = digest.digest();

				if (!MessageDigest.isEqual(except, computed)) throw new SecurityException("清单校验失败");
			}
			digestStr = main.getValue(algorithm+"-Digest-Manifest-Main-Attributes");
			if (digestStr != null && (flag & 2) == 0) {
				flag |= 2;

				MessageDigest digest = digester(digests, algorithm);
				byte[] computed = manifestBytes.digest(digest, null);
				byte[] except = Base64.decode(digestStr, IOUtil.getSharedByteBuf()).toByteArray();

				if (!MessageDigest.isEqual(except, computed)) throw new SecurityException("清单主属性校验失败");
			}

			if (flag == 3) {
				prevName = algorithm+"-Digest";
				break;
			}
		}
		if ((flag&3) == 0) throw new SecurityException("找不到清单哈希或清单主属性哈希");

		if (hashes != null) {
			for (Map.Entry<String, String> entry : hashes.entrySet()) {
				MessageDigest digest = digester(digests, algorithm);

				byte[] computed = manifestBytes.digest(digest, entry.getKey());
				byte[] except = Base64.decode(entry.getValue(), IOUtil.getSharedByteBuf()).toByteArray();

				if (!MessageDigest.isEqual(except, computed)) throw new SecurityException("清单"+entry.getKey()+"属性校验失败");
			}
		} else {
			// Deprecated
			for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
				String value = entry.getValue().getValue(prevName);
				block:
				if (value == null) {
					int i = 0;
					do {
						algorithm = SECURE_HASH_ALGORITHMS.get(i);
						value = entry.getValue().getValue(prevName = algorithm+"-Digest");
						if (value != null) break block;
						i++;
					} while (i < SECURE_HASH_ALGORITHMS.size());

					continue;
				}

				MessageDigest digest = digester(digests, algorithm);

				byte[] computed = manifestBytes.digest(digest, entry.getKey());
				byte[] except = Base64.decode(value, IOUtil.getSharedByteBuf()).toByteArray();

				if (!MessageDigest.isEqual(except, computed)) throw new SecurityException("清单"+entry.getKey()+"属性校验失败");
			}
		}

		Signature sign = Signature.getInstance(block.getSignatureAlg());
		sign.initVerify(block.getSigner().getPublicKey());
		sign.update(signatureBytes);
		if (!sign.verify(block.getSignature())) throw new SecurityException("元签名校验失败");

		try {
			readManifestAndHash(manifestBytes, lenient);
		} catch (IOException e) {
			throw new SecurityException("清单格式错误（这不应该发生）");
		}
		this.manifestBytes = null;
		this.signatureBytes = null;
		this.source = new CodeSource(base, new CodeSigner[]{new CodeSigner(block, null)});
		DIGESTS.set(digests);
	}
	private boolean readManifestAndHash(ManifestBytes manifestBytes, boolean lenient) throws IOException {
		this.hashes = new MyHashMap<>();

		MyHashMap<String, String> tmpMap = new MyHashMap<>();
		String prevName = null, algorithm = null;
		outerLoop:
		for (ManifestBytes.NamedAttr attribute : manifestBytes.namedAttrMap.values()) {
			tmpMap.clear();
			for (ManifestBytes.ByteAttr section : attribute.sections) {
				section.getAllLines(manifestBytes.data, tmpMap);
			}

			if (prevName != null) {
				String hash = tmpMap.get(prevName);
				if (hash != null) {
					hashes.put(attribute.name, hash);
					continue;
				}
			} else {
				for (int i = 0; i < SECURE_HASH_ALGORITHMS.size(); i++) {
					algorithm = SECURE_HASH_ALGORITHMS.get(i);
					String hash = tmpMap.get(prevName = algorithm + "-Digest");
					if (hash != null) {
						hashes.put(attribute.name, hash);
						continue outerLoop;
					}
				}
			}
			if (!lenient) throw new IOException("无法验证清单属性"+attribute.name+"="+tmpMap+": 不安全的校验码，或在同一个文件中使用了不同种类的校验码，开启lenient模式忽略此错误");
			else {
				hashes = null;
				manifest = new Manifest(new ByteArrayInputStream(manifestBytes.data));
				return false;
			}
		}

		manifest = new Manifest(new ByteArrayInputStream(manifestBytes.data, 0, manifestBytes.mainAttr.endOfSection+3));
		this.algorithm = algorithm;
		this.prevName = prevName;
		return true;
	}

	public Manifest getManifest() { return manifest; }
	public CodeSource getCodeSource() { return source; }

	private static MessageDigest digester(Map<String, MessageDigest> digests, String algorithm) throws NoSuchAlgorithmException {
		MessageDigest digest = digests.get(algorithm);
		if (digest == null) digests.put(algorithm, digest = MessageDigest.getInstance(algorithm));
		return digest;
	}
	private static MessageDigest digesterLazyReuse(Map<String, MessageDigest> digests, String algorithm) throws NoSuchAlgorithmException {
		MessageDigest digest = digests.remove(algorithm);
		if (digest == null) digest = MessageDigest.getInstance(algorithm);
		return digest;
	}

	public InputStream wrapInput(String name, InputStream in) {
		String except;
		if (hashes != null) {
			except = hashes.get(name);
			if (except == null) return in;
		} else {
			// Deprecated
			Attributes attr = manifest.getAttributes(name);
			if (attr == null) return in;

			except = attr.getValue(prevName);
			block:
			if (except == null) {
				for (int i = 0; i < SECURE_HASH_ALGORITHMS.size(); i++) {
					algorithm = SECURE_HASH_ALGORITHMS.get(i);
					except = attr.getValue(prevName = algorithm+"-Digest");
					if (except != null) break block;
				}
				return in;
			}
		}

		var digests = DIGESTS.get();
		if (digests == null) {
			digests = new MyHashMap<>();
			DIGESTS.set(digests);
		}

		MessageDigest md;
		try {
			md = digesterLazyReuse(digests, algorithm);
		} catch (NoSuchAlgorithmException e) {
			return in;
		}

		md.reset();
		return new DigestInputStream(in, md, except, name);
	}
	private static final class DigestInputStream extends InputStream {
		private final InputStream in;
		private MessageDigest md;
		private final String hash;
		private final String name;

		public DigestInputStream(InputStream in, MessageDigest md, String hash, String name) {
			this.in = in;
			this.md = md;
			this.hash = hash;
			this.name = name;
		}

		static void init() {}

		public int available() throws IOException { return in.available(); }

		public void close() throws IOException { in.close(); }

		public int read() throws IOException {
			int b = in.read();
			if (b >= 0) md.update((byte) b);
			else check();
			return b;
		}

		public int read(@NotNull byte[] b, int off, int len) throws IOException {
			len = in.read(b, off, len);
			if (len >= 0) md.update(b, off, len);
			else check();
			return len;
		}

		private synchronized void check() {
			if (md == null) return;

			var digest = md;
			byte[] result = digest.digest();
			md = null;
			DIGESTS.get().put(digest.getAlgorithm(), digest);

			var out = new CharList();
			if (!Base64.encode(DynByteBuf.wrap(result), out).equals(hash)) {
				throw new SecurityException(name+"的"+digest.getAlgorithm()+"校验失败");
			}
			out._free();
		}
	}

	// classloading hook
	static {DigestInputStream.init();}
	@Nullable
	public static JarVerifier create(ZipFile zf) throws IOException {
		InputStream in = zf.getStream("META-INF/MANIFEST.MF");
		if (in == null) return null;

		Manifest manifest = null;
		ManifestBytes mb = null;
		byte[] sb = null;
		SignatureBlock signatureBlock = null;

		for (ZEntry entry : zf.entries()) {
			String name = entry.getName();
			String extName = IOUtil.extensionName(name);
			if (name.startsWith("META-INF/") && VALID_CERTIFICATE_EXTENSION.contains(extName)) {
				try {
					signatureBlock = new SignatureBlock(zf.getStream(entry));
				} catch (CertificateException e) {
					throw new IOException(e);
				}

				var in1 = zf.getStream(name.substring(0, name.length() - extName.length())+"SF");
				if (in1 == null) signatureBlock = null;
				else {
					sb = IOUtil.read(in1);
					mb = new ManifestBytes(in);
				}

				break;
			}
		}
		if (mb == null) manifest = new Manifest(in);

		IOUtil.closeSilently(in);
		URL url = zf.source() instanceof FileSource file ? new URL("file", "", file.getFile().getAbsolutePath().replace(File.separatorChar, '/')) : null;
		return new JarVerifier(url, manifest, mb, sb, signatureBlock);
	}

	/**
	 * 一键对jar签名
	 * @param zf 准备签名的文件
	 * @param certs 证书链
	 * @param prk 私钥，对应第一个证书
	 * @param options 选项  jarSigner:signatureHashAlgorithm jarSigner:manifestHashAlgorithm jarSigner:skipPerFileAttributes jarSigner:signatureFileName jarSigner:cacheHash jarSigner:creator
	 */
	public static void signJar(ZipArchive zf, List<Certificate> certs, PrivateKey prk, Map<String, String> options) throws GeneralSecurityException, IOException {
		String signAlg = certs.get(0).getPublicKey().getAlgorithm();
		var signHashAlg = options.getOrDefault("jarSigner:signatureHashAlgorithm", "SHA-256");

		var signer = Signature.getInstance(signHashAlg.replace("-", "")+"with"+(signAlg.equals("EC")?"ECDSA": signAlg));

		var hashAlg = options.getOrDefault("jarSigner:manifestHashAlgorithm", "SHA-256");
		if (!VALID_CERTIFICATE_EXTENSION.contains(signAlg.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("不支持的数字签名类型:"+signAlg);
		if (!SECURE_HASH_ALGORITHMS.contains(hashAlg) || !SECURE_HASH_ALGORITHMS.contains(signHashAlg)) throw new IllegalArgumentException("不支持的哈希函数类型:"+options);

		var md = MessageDigest.getInstance(hashAlg);

		byte[] buf = new byte[1024];
		var mfin = zf.getStream("META-INF/MANIFEST.MF");
		if (mfin == null) throw new FileNotFoundException("META-INF/MANIFEST.MF");
		var mf = new Manifest(mfin);
		IOUtil.closeSilently(mfin);

		var cacheHash = "true".equals(options.get("jarSigner:cacheHash"));

		var digestKey = new Attributes.Name(hashAlg+"-Digest");
		for (ZEntry entry : zf.entries()) {
			if (entry.getName().startsWith("META-INF/")) {
				if (entry.getName().equals("META-INF/MANIFEST.MF")) continue;
				if (VALID_CERTIFICATE_EXTENSION.contains(IOUtil.extensionName(entry.getName()))) {
					String oldSfName = "META-INF/"+IOUtil.fileName(entry.getName())+".SF";
					zf.put(entry.getName(), null);
					zf.put(oldSfName, null);
					mf.getEntries().remove(entry.getName());
					mf.getEntries().remove(oldSfName);
					continue;
				}
				if (entry.getName().endsWith(".SF")) continue;
			}

			var subattr = mf.getAttributes(entry.getName());
			if (subattr == null) {
				subattr = new Attributes(1);
				mf.getEntries().put(entry.getName(), subattr);
			}

			if (cacheHash) {
				var prevHash = options.get(digestKey+":"+entry.getName());
				if (prevHash != null) {
					subattr.put(digestKey, prevHash);
					continue;
				}
			}

			try (var in = zf.getStream(entry)) {
				while (true) {
					int r = in.read(buf);
					if (r < 0) break;
					md.update(buf, 0, r);
				}

				var digest = Base64.encode(DynByteBuf.wrap(md.digest()), IOUtil.getSharedCharBuf()).toString();
				subattr.put(digestKey, digest);
				if (cacheHash) options.put(digestKey+":"+entry.getName(), digest);
			}
		}

		var ob = IOUtil.getSharedByteBuf();
		mf.write(ob);
		byte[] mfBytes = ob.toByteArray();
		zf.put("META-INF/MANIFEST.MF", new ByteList(mfBytes));

		var mb = new ManifestBytes(mfBytes);

		ob.clear();
		ob.putAscii("Signature-Version: 1.0\r\n");
		append72(ob, "Created-By: "+options.getOrDefault("jarSigner:creator", CREATED_BY));
		append72(ob, hashAlg+"-Digest-Manifest: "+IOUtil.encodeBase64(md.digest(mfBytes)));
		append72(ob, hashAlg+"-Digest-Manifest-Main-Attributes: "+IOUtil.encodeBase64(mb.digest(md, null)));
		ob.putAscii("\r\n");

		if (!"true".equals(options.get("jarSigner:skipPerFileAttributes"))) {
			for (String name : mb.namedAttrMap.keySet()) {
				append72(ob, "Name: "+name);
				append72(ob, digestKey+": "+IOUtil.encodeBase64(mb.digest(md, name)));
				ob.putAscii("\r\n");
			}
		}

		var sfName = options.getOrDefault("jarSigner:signatureFileName", "SIGNFILE");

		byte[] sfBytes = ob.toByteArray();
		zf.put("META-INF/"+sfName+".SF", DynByteBuf.wrap(sfBytes));

		signer.initSign(prk);
		signer.update(sfBytes);
		byte[] signature = signer.sign();

		var sb = new SignatureBlock(certs, signature, signer.getAlgorithm(), 0, null);
		zf.put("META-INF/"+sfName+"."+signAlg, DynByteBuf.wrap(sb.getEncoded("PKCS7")));
	}

	private static void append72(DynByteBuf out, String line) {
		if (!line.isEmpty()) {
			var tmp = new ByteList().putUTFData(line);

			var lineBytes = tmp.list;
			int length = tmp.wIndex();
			// first line can hold one byte more than subsequent lines which
			// start with a continuation line break space
			out.write(lineBytes[0]);
			int pos = 1;
			while (length - pos > 71) {
				out.write(lineBytes, pos, 71);
				pos += 71;
				out.putAscii("\r\n");
				out.write(' ');
			}
			out.write(lineBytes, pos, length - pos);
		}
		out.putAscii("\r\n");
	}

	public static void main(String[] args) throws Exception {
		try (var zf = new ZipFile(args[0])) {
			JarVerifier verifier = create(zf);
			if (verifier == null) {
				System.out.println("文件没有清单属性");
				return;
			}
			if (!verifier.isSigned()) {
				System.out.println("文件没有签名");
				return;
			}

			verifier.ensureManifestValid(false);
			System.out.println("清单和元签名校验通过");
			System.out.println("是自签证书:"+!verifier.isSignTrusted());
			System.out.println("签名算法:"+verifier.block.getSignatureAlg());

			for (ZEntry entry : zf.entries()) {
				try (InputStream in = verifier.wrapInput(entry.getName(), zf.getStream(entry))) {
					IOUtil.read(in);
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}

			System.out.println("文件验证完成，有问题的文件已在上方列出");
		}
	}
}