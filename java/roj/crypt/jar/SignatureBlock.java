package roj.crypt.jar;

import roj.collect.HashMap;
import roj.config.ParseException;
import roj.config.data.CByteArray;
import roj.config.data.CIntArray;
import roj.crypt.CryptoFactory;
import roj.crypt.asn1.Asn1Context;
import roj.crypt.asn1.DerReader;
import roj.crypt.asn1.DerValue;
import roj.crypt.asn1.DerWriter;
import roj.io.IOUtil;
import roj.io.MyDataInputStream;
import roj.text.TextUtil;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.cert.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roj234
 * @since 2024/3/25 4:13
 */
final class SignatureBlock extends CertPath {
	private final List<X509Certificate> certs;
	private final String signatureAlg;
	private final X509Certificate signer;
	private final byte[] signature, sfData;

	private static final String PKCS7_ENCODING = "PKCS7", PKIPATH_ENCODING = "PkiPath";

	private static final Asn1Context PKCS7;
	static {
		try {
			PKCS7 = Asn1Context.createFromString(IOUtil.getTextResourceIL("roj/crypt/jar/PKCS#7.asn"));
		} catch (ParseException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final Collection<String> encodingList = List.of(PKIPATH_ENCODING, PKCS7_ENCODING);

	private static final HashMap<CIntArray, String> DigestAlg = new HashMap<>(), SignAlg = new HashMap<>();
	static {
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.1"), "SHA256");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.2"), "SHA384");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.3"), "SHA512");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.4"), "SHA224");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.5"), "SHA512-224");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.6"), "SHA512-256");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.7"), "SHA3-224");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.8"), "SHA3-256");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.9"), "SHA3-384");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.10"), "SHA3-512");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.11"), "SHAKE128");
		DigestAlg.put(DerValue.OID("2.16.840.1.101.3.4.2.12"), "SHAKE256");
		SignAlg.put(DerValue.OID("1.2.840.113549.1.1.1"), "RSA");
		SignAlg.put(DerValue.OID("1.2.840.113549.1.1.10"), "RSASSA-PSS");
		SignAlg.put(DerValue.OID("1.2.840.10040.4.1"), "DSA");
		SignAlg.put(DerValue.OID("1.2.840.10045.4.1"), "ECDSA");
	}

	@SuppressWarnings("unchecked")
	public SignatureBlock(List<? extends Certificate> certs, byte[] signature, String signatureAlg, int signerId, byte[] sfData) throws CertificateException {
		super("X.509");

		for (Object o : certs) {
			if (!(o instanceof X509Certificate)) {
				throw new CertificateException("not X509Certificates:"+ o.getClass().getName());
			}
		}

		// 这个数组还要复制两遍，哎真是，GC GC，仗着有GC为所欲为.jpg
		this.certs = ArrayUtil.immutableCopyOf((List<X509Certificate>) certs);
		this.signature = signature;
		this.signatureAlg = signatureAlg;
		this.signer = (X509Certificate) certs.get(signerId);
		this.sfData = sfData;
	}

	public SignatureBlock(InputStream is) throws CertificateException {
		super("X.509");
		if (is == null) throw new CertificateException("input stream is null");

		try {
			var der = new DerReader(MyDataInputStream.wrap(is));

			var info = PKCS7.parse("ContentInfo", der).asMap().getMap("content");

			var certBytes = info.getList("certificates");
			var factory = CertificateFactory.getInstance("X.509");
			var cert = new X509Certificate[certBytes.size()];

			var buf = IOUtil.getSharedByteBuf();
			for (int i = 0; i < certBytes.size(); i++) {
				byte[] data = ((DerValue.Opaque)(((DerValue.Choice) certBytes.get(i)).ref)).value;

				buf.clear();
				buf.put(DerValue.SEQUENCE).put(0x82).putShort(data.length).put(data);

				cert[i] = (X509Certificate) factory.generateCertificate(buf.asInputStream());
			}

			// certs are optional
			certs = cert.length > 0 ? List.of(cert) : Collections.emptyList();

			var signerInfos = info.getList("signerInfos");
			if (signerInfos.size() != 1) throw new UnsupportedOperationException("暂不支持多签名！");

			String digestOid = null, signOid = null;
			byte[] sign = null;
			X509Certificate cert1 = null;

			for (int i = 0; i < signerInfos.size(); i++) {
				var signerInfo = signerInfos.getMap(i);

				DerWriter dw = new DerWriter();
				PKCS7.write("Name", signerInfo.query("sid.issuer"), dw);
				buf.clear();
				dw.flush(buf);
				byte[] issuer = buf.toByteArray();

				BigInteger sn = ((DerValue.Int)signerInfo.query("sid.serialNumber")).value;

				//noinspection SuspiciousMethodCalls
				digestOid = DigestAlg.get(signerInfo.query("digestAlgorithm.algorithm"));
				//noinspection SuspiciousMethodCalls
				signOid = SignAlg.get(signerInfo.query("signatureAlgorithm.algorithm"));
				sign = (byte[]) signerInfo.get("signature").unwrap();

				block: {
					for (int j = 0; j < certs.size(); j++) {
						cert1 = certs.get(j);
						// 主要是X509证书未来可能增加新的格式，不然我就自己做了……
						if (Arrays.equals(cert1.getIssuerX500Principal().getEncoded(), issuer) && cert1.getSerialNumber().equals(sn)) break block;
					}
					throw new CertificateEncodingException("未在证书链中找到签名者#"+i+"的序列号");
				}
			}

			signer = cert1;
			signatureAlg = digestOid+"with"+signOid;
			signature = sign;
			var inlinesf = (CByteArray)info.query("contentInfo.content");
			sfData = inlinesf == null ? null : inlinesf.value;
		} catch (CertificateException e) {
			throw e;
		} catch (Exception e) {
			throw new CertificateException("PKCS7 envelope解析失败: "+e.getMessage());
		}
	}

	public void checkTrusted() throws CertPathValidatorException {
		try {
			CertPathValidator validator = CertPathValidator.getInstance("PKIX");
			PKIXParameters params = new PKIXParameters(Arrays.stream(CryptoFactory.getDefaultTrustStore().getAcceptedIssuers()).map(x -> new TrustAnchor(x, null)).collect(Collectors.toSet()));
			params.setRevocationEnabled(true);
			validator.validate(this, params);
		} catch (CertPathValidatorException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("意外的验证失败", e);
		}
	}

	public byte[] getSfData(byte[] sfData) {return this.sfData == null ? sfData : this.sfData;}

	@Override
	public byte[] getEncoded() throws CertificateEncodingException {
		DerWriter writer = new DerWriter();
		writer.begin(DerValue.SEQUENCE);
		for (int i = certs.size() - 1; i >= 0; i--) {
			byte[] encoded = certs.get(i).getEncoded();
			writer.write(DynByteBuf.wrap(encoded));
		}
		writer.end();

		ByteList buf = IOUtil.getSharedByteBuf();
		writer.flush(buf);
		return buf.toByteArray();
	}

	private byte[] encodePKCS7() throws CertificateEncodingException {
		var withs = TextUtil.split(signatureAlg, "with");
		int[] digestAlg, signAlg;
		block: {
			for (Map.Entry<CIntArray, String> entry : SignAlg.entrySet()) {
				if (entry.getValue().equals(withs.get(1))) {
					signAlg = entry.getKey().value;
					break block;
				}
			}
			throw new IllegalStateException("Unknown signature algorithm "+withs.get(1));
		}
		block: {
			for (Map.Entry<CIntArray, String> entry : DigestAlg.entrySet()) {
				if (entry.getValue().equals(withs.get(0))) {
					digestAlg = entry.getKey().value;
					break block;
				}
			}
			throw new IllegalStateException("Unknown digest algorithm "+withs.get(0));
		}
		DynByteBuf issuer = null;

		DerWriter dw = new DerWriter();
		dw.begin(DerValue.SEQUENCE);//ContentInfo
		dw.writeOid(1,2,840,113549,1,7,2);//OID=SignedData
		dw.begin(0xA0);//EXPLICIT contentType
		dw.begin(DerValue.SEQUENCE);//SignedData
		dw.writeInt(BigInteger.ONE);//version
		dw.begin(DerValue.SET);//DigestAlgorithmIdentifiers
		dw.begin(DerValue.SEQUENCE);//DigestAlgorithmIdentifier
		dw.writeOid(digestAlg);//Algorithm
		dw.writeNull();//OPTIONAL parameters
		dw.end();//end=DigestAlgorithmIdentifier
		dw.end();//end=DigestAlgorithmIdentifiers
		dw.begin(DerValue.SEQUENCE);//ContentInfo
		dw.writeOid(1,2,840,113549,1,7,1);// data (PKCS#7)
		if (sfData != null) {
			dw.begin(0xA0);//data
			dw.writeBytes(sfData);
			dw.end();//end=data
		}
		dw.end();//end=ContentInfo
		dw.begin(0xA0);//OPTIONAL IMPLICIT[0] certificates
		for (X509Certificate cert : certs) {
			ByteList din = new ByteList(cert.getEncoded());
			DerReader in = new DerReader(din);

			try {
				in.readType();
				in.readLength();
				dw.write(DerValue.SEQUENCE, din.slice());//SEQUENCE certificate

				if (cert != signer) continue;

				//TBS
				in.readType();
				int len = in.readLength();

				//version
				in.readType();
				len = in.readLength();
				in.skip(len);

				//serialNumber
				in.readType();
				len = in.readLength();
				in.skip(len);

				//signature
				in.readType();
				in.skip(in.readLength());

				//issuer
				int type = in.readType();
				assert type == DerValue.SEQUENCE;
				issuer = din.slice(in.readLength());
			} catch (IOException e) {
				assert false;
			}

		}
		dw.end();//end=certificates
		//OPTIONAL crls
		dw.begin(DerValue.SET);//SignerInfos
		dw.begin(DerValue.SEQUENCE);//SignerInfo

		dw.writeInt(BigInteger.ONE);//version
		dw.begin(DerValue.SEQUENCE);//sid SignerIdentifier => IssuerAndSerialNumber
		assert issuer != null;
		dw.write(DerValue.SEQUENCE, issuer);//Issuer
		dw.writeInt(signer.getSerialNumber());//CertificateSerialNumber
		dw.end();//end=sid
		dw.begin(DerValue.SEQUENCE);//DigestAlgorithmIdentifier
		dw.writeOid(digestAlg);//Algorithm
		dw.writeNull();//OPTIONAL parameters
		dw.end();//end=DigestAlgorithmIdentifier
		//OPTIONAL SignedAttributes
		dw.begin(DerValue.SEQUENCE);//SignatureAlgorithmIdentifier
		dw.writeOid(signAlg);//Algorithm
		dw.writeNull();//OPTIONAL parameters
		dw.end();//end=SignatureAlgorithmIdentifier
		dw.writeBytes(signature);//signature
		if (false) {
			dw.begin(0xA1);//OPTIONAL UnsignedAttributes
			dw.begin(DerValue.SEQUENCE);
			dw.writeOid(1,3,6,1,5,5,7,3,8);
			// 2016-03-17 16:40:46 UTC
			//dw.writeIso(DerValue.UTCTime, ACalendar.GMT().format("Y-m-d H:i:s", System.currentTimeMillis()).append(" UTC").toString());
			dw.end();//end=attributes
			dw.end();//end=UnsignedAttributes
		}
		dw.end();//end=SignerInfo
		dw.end();//end=SignerInfos
		dw.end();//end=SignedData
		dw.end();//end=contentType
		dw.end();//end=ContentInfo

		ByteList buf = IOUtil.getSharedByteBuf();
		dw.flush(buf);
		return buf.toByteArray();
	}

	@Override
	public byte[] getEncoded(String encoding) throws CertificateEncodingException {
		return switch (encoding) {
			case PKIPATH_ENCODING -> getEncoded();
			case PKCS7_ENCODING -> encodePKCS7();
			default -> throw new CertificateEncodingException("unsupported encoding");
		};
	}

	@Override
	public Iterator<String> getEncodings() { return encodingList.iterator(); }

	@Override
	public List<X509Certificate> getCertificates() { return certs; }

	public byte[] getSignature() { return signature; }
	public String getSignatureAlg() { return signatureAlg; }
	public X509Certificate getSigner() {return signer;}
}