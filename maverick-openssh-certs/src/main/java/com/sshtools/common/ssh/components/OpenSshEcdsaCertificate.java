/**
 * (c) 2002-2019 JADAPTIVE Limited. All Rights Reserved.
 *
 * This file is part of the Maverick Synergy Java SSH API.
 *
 * Maverick Synergy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Maverick Synergy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Maverick Synergy.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.sshtools.common.ssh.components;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;

import com.sshtools.common.logger.Log;
import com.sshtools.common.ssh.SecurityLevel;
import com.sshtools.common.ssh.SshException;
import com.sshtools.common.ssh.SshKeyFingerprint;
import com.sshtools.common.ssh.components.jce.ECUtils;
import com.sshtools.common.ssh.components.jce.JCEAlgorithms;
import com.sshtools.common.ssh.components.jce.JCEProvider;
import com.sshtools.common.util.ByteArrayReader;
import com.sshtools.common.util.ByteArrayWriter;
import com.sshtools.common.util.SimpleASNWriter;

public class OpenSshEcdsaCertificate extends OpenSshCertificate implements SshPublicKey {

	
	
	byte[] nonce;
	String name;
	String spec;
	String curve;

	protected ECPublicKey pub;

	public OpenSshEcdsaCertificate(ECPublicKey pub, String curve) throws IOException {
		super(SecurityLevel.STRONG, 2000);
		this.pub = pub;
		if (curve.equals("prime256v1") || curve.equals("secp256r1")) {
			this.curve = "secp256r1";
			this.name = "ecdsa-sha2-nistp256-cert-v01@openssh.com";
			this.spec = JCEAlgorithms.JCE_SHA256WithECDSA;
		} else if (curve.equals("secp384r1")) {
			this.curve = "secp384r1";
			this.name = "ecdsa-sha2-nistp384-cert-v01@openssh.com";
			this.spec = JCEAlgorithms.JCE_SHA384WithECDSA;
		} else if (curve.equals("secp521r1")) {
			this.curve = "secp521r1";
			this.name = "ecdsa-sha2-nistp521-cert-v01@openssh.com";
			this.spec = JCEAlgorithms.JCE_SHA512WithECDSA;
		} else {
			throw new IOException("Unsupported curve name " + curve);
		}
	}

	OpenSshEcdsaCertificate(String name, String spec, String curve) {
		super(SecurityLevel.STRONG, 2000);
		this.name = name;
		this.spec = spec;
		this.curve = curve;
	}

	public void init(byte[] blob, int start, int len) throws SshException {

		ByteArrayReader buf = new ByteArrayReader(blob, start, len);
		try {

			@SuppressWarnings("unused")
			String type = buf.readString();

			nonce = buf.readBinaryString();
			
			buf.readString();
			byte[] Q = buf.readBinaryString();

			decode(buf);
			
			ECParameterSpec ecspec = getCurveParams(curve);

			ECPoint p = ECUtils.fromByteArray(Q, ecspec.getCurve());
			KeyFactory keyFactory = JCEProvider
					.getProviderForAlgorithm(JCEProvider.getECDSAAlgorithmName()) == null ? KeyFactory
					.getInstance(JCEProvider.getECDSAAlgorithmName()) : KeyFactory
					.getInstance(JCEProvider.getECDSAAlgorithmName(), JCEProvider
							.getProviderForAlgorithm(JCEProvider.getECDSAAlgorithmName()));
			pub = (ECPublicKey) keyFactory.generatePublic(new ECPublicKeySpec(
					p, ecspec));
		} catch (Throwable t) {
			Log.error("Failed to decode public key blob", t);
			throw new SshException("Failed to decode public key blob",
					SshException.INTERNAL_ERROR);
		} finally {
			buf.close();
		}

	}

	public String getAlgorithm() {
		return name;
	}

	public int getBitLength() {
		return pub.getParams().getOrder().bitLength();
	}

	public byte[] getEncoded() throws SshException {

		ByteArrayWriter blob = new ByteArrayWriter();

		try {

			blob.writeString(getEncodingAlgorithm());
			blob.writeBinaryString(nonce);
			blob.writeString(name.substring(name.lastIndexOf("-") + 1));
			blob.writeBinaryString(getPublicOctet());
			encode(blob);
			
			return blob.toByteArray();
		} catch (Throwable t) {
			throw new SshException("Failed to encode public key",
					SshException.INTERNAL_ERROR);
		} finally {
			try {
				blob.close();
			} catch (IOException e) {
			}
		}

	}

	public byte[] getPublicOctet() {
		return ECUtils.toByteArray(pub.getW(), pub.getParams()
				.getCurve());
	}

	public String getFingerprint() throws SshException {
		return SshKeyFingerprint.getFingerprint(getEncoded());
	}

	public boolean verifySignature(byte[] signature, byte[] data)
			throws SshException {

		ByteArrayReader bar = new ByteArrayReader(signature);
		try {

			int len = (int) bar.readInt();
			// Check for differing version of the transport protocol
			if (bar.available() > len) {

				byte[] sig = new byte[len];
				bar.read(sig);

				// Log.debug("Signature blob is " + new String(sig));
				String header = new String(sig);

				if (!header.equals(name)) {
					throw new SshException(
							"The encoded signature is not ECDSA",
							SshException.INTERNAL_ERROR);
				}

				signature = bar.readBinaryString();
			}

			// Using a SimpleASNWriter

			bar.close();

			bar = new ByteArrayReader(signature);
			BigInteger r = bar.readBigInteger();
			BigInteger s = bar.readBigInteger();

			SimpleASNWriter asn = new SimpleASNWriter();
			asn.writeByte(0x02);
			asn.writeData(r.toByteArray());
			asn.writeByte(0x02);
			asn.writeData(s.toByteArray());

			SimpleASNWriter asnEncoded = new SimpleASNWriter();
			asnEncoded.writeByte(0x30);
			asnEncoded.writeData(asn.toByteArray());

			byte[] encoded = asnEncoded.toByteArray();

			Signature sig = JCEProvider.getProviderForAlgorithm(spec) == null ? Signature
					.getInstance(spec) : Signature.getInstance(spec,
					JCEProvider.getProviderForAlgorithm(spec));
			sig.initVerify(pub);
			sig.update(data);

			return sig.verify(encoded);
		} catch (Exception ex) {
			throw new SshException(SshException.JCE_ERROR, ex);
		} finally {
			bar.close();
		}

	}

	public ECParameterSpec getCurveParams(String curve) {
		try {
			KeyPairGenerator gen = JCEProvider
					.getProviderForAlgorithm(JCEProvider.getECDSAAlgorithmName()) == null ? KeyPairGenerator
					.getInstance(JCEProvider.getECDSAAlgorithmName()) : KeyPairGenerator
					.getInstance(JCEProvider.getECDSAAlgorithmName(), JCEProvider
							.getProviderForAlgorithm(JCEProvider.getECDSAAlgorithmName()));

			gen.initialize(new ECGenParameterSpec(curve),
					JCEProvider.getSecureRandom());
			KeyPair tmp = gen.generateKeyPair();
			return ((ECPublicKey) tmp.getPublic()).getParams();
		} catch (Throwable t) {
		}
		return null;
	}



	public PublicKey getJCEPublicKey() {
		return pub;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pub == null) ? 0 : pub.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OpenSshEcdsaCertificate other = (OpenSshEcdsaCertificate) obj;
		if (pub == null) {
			if (other.pub != null)
				return false;
		} else if (!pub.equals(other.pub))
			return false;
		return true;
	}

	@Override
	public String getSigningAlgorithm() {
		return getAlgorithm();
	}
	
	@Override
	public String test() {
		try {
			KeyFactory keyFactory = JCEProvider
					.getProviderForAlgorithm(JCEProvider.getECDSAAlgorithmName()) == null ? KeyFactory
					.getInstance(JCEProvider.getECDSAAlgorithmName()) : KeyFactory
					.getInstance(JCEProvider.getECDSAAlgorithmName(), JCEProvider
							.getProviderForAlgorithm(JCEProvider.getECDSAAlgorithmName()));
					
			@SuppressWarnings("unused")
			Signature sig = JCEProvider.getProviderForAlgorithm(spec) == null ? Signature
					.getInstance(spec) : Signature.getInstance(spec,
					JCEProvider.getProviderForAlgorithm(spec));
					
			return keyFactory.getProvider().getName();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
}
