package ru.ashemchuk.generation;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class CertificateGenerator {
    public X509Certificate generate(
        String name,
        PublicKey publicKey,
        PrivateKey issuerKey,
        String issuerName
    ) throws OperatorCreationException, CertificateException {

        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, name)
            .build();

        X500Name issuer = new X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, issuerName)
            .build();



        BigInteger serial = new BigInteger(159, new SecureRandom())
            .setBit(158);

        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));

        SubjectPublicKeyInfo keyInfo =
            SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            keyInfo
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA384withECDSA")
            .build(issuerKey);

        X509CertificateHolder holder = builder.build(signer);

        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}
