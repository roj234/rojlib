package roj.crypt.jar;

import roj.text.ParseException;
import roj.crypt.CryptoFactory;
import roj.crypt.asn1.*;
import roj.io.IOUtil;
import roj.io.MyDataInputStream;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.function.Flow;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.cert.*;
import java.util.*;

/**
 * @author Roj234
 * @since 2024/3/25 4:13
 */
final class SignatureBlock extends CertPath {
	private final List<X509Certificate> certs;
	private final String digestAlg, signatureAlg;
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

	@SuppressWarnings("unchecked")
	public SignatureBlock(List<? extends Certificate> certs, byte[] signature, String digestAlg, String signatureAlg, int signerId, byte[] sfData) throws CertificateException {
		super("X.509");

		for (Object o : certs) {
			if (!(o instanceof X509Certificate)) {
				throw new CertificateException("not X509Certificates:"+ o.getClass().getName());
			}
		}

		// 这个数组还要复制两遍，哎真是，GC GC，仗着有GC为所欲为.jpg
		this.certs = ArrayUtil.immutableCopyOf((List<X509Certificate>) certs);
		this.signature = signature;
		this.digestAlg = digestAlg;
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
			if (signerInfos.size() != 1) {
				if (signerInfos.size() == 0) {
					digestAlg = null;
					signatureAlg = null;
					signer = null;
					signature = null;
					sfData = null;
					return;
				}

				throw new UnsupportedOperationException("暂不支持多签名！");
			}

			KnownOID digestOid = null, signOid = null;
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

				digestOid = KnownOID.valueOf(signerInfo.query("digestAlgorithm.algorithm")).assertType("hashAlg");
				signOid = KnownOID.valueOf(signerInfo.query("signatureAlgorithm.algorithm"));
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
			digestAlg = digestOid.name();
			signatureAlg = signOid.name().contains("with") ? signOid.assertType("signAlg").name() : digestOid+"with"+signOid.assertType("signType");
			signature = sign;
			sfData = (byte[]) info.query("contentInfo.content").unwrap();
		} catch (CertificateException e) {
			throw e;
		} catch (Exception e) {
			throw new CertificateException("PKCS7 envelope解析失败: "+e.getMessage());
		}
	}

	public void checkTrusted() throws CertPathValidatorException {
		try {
			CertPathValidator validator = CertPathValidator.getInstance("PKIX");
			PKIXParameters params = new PKIXParameters(Flow.of(CryptoFactory.getDefaultTrustStore().getAcceptedIssuers()).map(x -> new TrustAnchor(x, null)).toSet());
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
		int[] signAlg = KnownOID.valueOf(signatureAlg).assertType("signAlg").oid.value,
			  hashAlg = KnownOID.valueOf(digestAlg).assertType("hashAlg").oid.value;

		DynByteBuf issuer = null;

		DerWriter dw = new DerWriter();
		dw.begin(DerValue.SEQUENCE);//ContentInfo
		dw.writeOid(1,2,840,113549,1,7,2);//OID=SignedData
		dw.begin(0xA0);//EXPLICIT contentType
		dw.begin(DerValue.SEQUENCE);//SignedData
		dw.writeInt(BigInteger.ONE);//version
		dw.begin(DerValue.SET);//DigestAlgorithmIdentifiers
		dw.begin(DerValue.SEQUENCE);//DigestAlgorithmIdentifier
		dw.writeOid(hashAlg);//Algorithm
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
		if (signature != null) {
			dw.begin(DerValue.SEQUENCE);//SignerInfo

			dw.writeInt(BigInteger.ONE);//version
			dw.begin(DerValue.SEQUENCE);//sid SignerIdentifier => IssuerAndSerialNumber
			assert issuer != null;
			dw.write(DerValue.SEQUENCE, issuer);//Issuer
			dw.writeInt(signer.getSerialNumber());//CertificateSerialNumber
			dw.end();//end=sid
			dw.begin(DerValue.SEQUENCE);//DigestAlgorithmIdentifier
			dw.writeOid(hashAlg);//Algorithm
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
		}
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