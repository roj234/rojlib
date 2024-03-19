/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package roj.crypt.jar;

import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.crypt.asn1.Asn1Context;
import roj.crypt.asn1.DerReader;
import roj.crypt.asn1.DerValue;
import roj.crypt.asn1.DerWriter;
import roj.io.IOUtil;
import roj.io.MyDataInputStream;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.cert.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/3/25 0025 4:13
 */
public class SignatureBlock extends CertPath {
	private final List<X509Certificate> certs;
	private final String signatureAlg;
	private final BigInteger signerSn;
	private final byte[] signature;

	private static final String PKCS7_ENCODING = "PKCS7", PKIPATH_ENCODING = "PkiPath";

	private static final Collection<String> encodingList = List.of(PKIPATH_ENCODING, PKCS7_ENCODING);

	private static final MyHashMap<DerValue.OID, String> DigestAlg = new MyHashMap<>(), SignAlg = new MyHashMap<>();
	static {
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.1"), "sha256");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.2"), "sha384");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.3"), "sha512");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.4"), "sha224");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.5"), "sha512-224");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.6"), "sha512-256");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.7"), "SHA3-224");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.8"), "SHA3-256");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.9"), "SHA3-384");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.10"), "SHA3-512");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.11"), "SHAKE128");
		DigestAlg.put(new DerValue.OID("2.16.840.1.101.3.4.2.12"), "SHAKE256");
		SignAlg.put(new DerValue.OID("1.2.840.113549.1.1.1"), "RSA");
		SignAlg.put(new DerValue.OID("1.2.840.113549.1.1.10"), "RSASSA-PSS");
		SignAlg.put(new DerValue.OID("1.2.840.10040.4.1"), "DSA");
	}

	@SuppressWarnings("unchecked")
	public SignatureBlock(List<? extends Certificate> certs, byte[] signature) throws CertificateException {
		super("X.509");

		for (Object o : certs) {
			if (!(o instanceof X509Certificate)) {
				throw new CertificateException("not X509Certificates:"+ o.getClass().getName());
			}
		}

		// 这个数组还要复制两遍，哎真是，GC GC，仗着有GC为所欲为.jpg
		this.certs = List.copyOf((List<X509Certificate>) certs);
		this.signature = signature;
		this.signatureAlg = null;
		this.signerSn = null;
	}

	private static final Asn1Context PKCS_7_CTX;
	static {
		try {
			PKCS_7_CTX = Asn1Context.createFromString(IOUtil.getTextResource("META-INF/asn1/PKCS#7.asn"));
		} catch (ParseException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	public SignatureBlock(InputStream is) throws CertificateException {
		super("X.509");
		if (is == null) throw new CertificateException("input stream is null");

		try {

			var der = new DerReader(MyDataInputStream.wrap(is));

			var info = PKCS_7_CTX.parse("ContentInfo", der);

			var signedData = info.collection().get(1).collection();

			var certBytes = signedData.get(3).collection();
			var signerInfos = signedData.get(5).collection();

			if (signerInfos.size() != 1) throw new UnsupportedOperationException("不支持的格式，请发issue！(signerInfos.size() != 1)");

			BigInteger sn = null;
			String digestOid = null, signOid = null;
			byte[] sign = null;

			for (int i = 0; i < signerInfos.size(); i++) {
				var signerInfo = signerInfos.get(i).collection();

				sn = ((DerValue.Int) signerInfo.get(1).collection().get(1)).integer;

				System.out.println(signerInfo.get(2).collection().get(0));
				digestOid = DigestAlg.get(signerInfo.get(2).collection().get(0));
				signOid = SignAlg.get(signerInfo.get(4).collection().get(0));
				sign = ((DerValue.Bytes) signerInfo.get(5)).data;
			}

			var factory = CertificateFactory.getInstance("X.509");
			var cert = new X509Certificate[certBytes.size()];

			var buf = IOUtil.getSharedByteBuf();
			for (int i = 0; i < certBytes.size(); i++) {
				byte[] data = certBytes.get(i).unparsedData();

				buf.clear();
				buf.put(DerValue.SEQUENCE).put(0x82).putShort(data.length).put(data);

				cert[i] = (X509Certificate) factory.generateCertificate(buf.asInputStream());
			}

			// certs are optional in PKCS #7
			certs = cert.length > 0 ? List.of(cert) : Collections.emptyList();
			signerSn = sn;
			signatureAlg = digestOid+"with"+signOid;
			signature = sign;
		} catch (Exception e) {
			throw new CertificateException("IOException parsing PKCS7 data: " + e, e);
		}
	}

	@Override
	public byte[] getEncoded() throws CertificateEncodingException {
		try {
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
		} catch (IOException e) {
			throw new CertificateEncodingException("IOException encoding PkiPath: "+e, e);
		}
	}

	private byte[] encodePKCS7() throws CertificateEncodingException {
		/*PKCS7 p7 = new PKCS7(new AlgorithmId[0],
			new ContentInfo(ContentInfo.DATA_OID, null),
			certs.toArray(new X509Certificate[certs.size()]),
			new SignerInfo[0]);
		DerOutputStream derout = new DerOutputStream();
		try {
			p7.encodeSignedData(derout);
		} catch (IOException ioe) {
			throw new CertificateEncodingException(ioe.getMessage());
		}
		return derout.toByteArray();*/
		return null;
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
	public BigInteger getSignerSn() { return signerSn; }
}