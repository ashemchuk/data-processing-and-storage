package ru.ashemchuk;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;

public class IssuerKeyTool {
    public static void main(String[] args) throws GeneralSecurityException, IOException {
        Path output = Path.of(args.length == 0 ? "config/SUPER_SECRET_ISSUER.key" : args[0]);

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp384r1"));
        ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);

        //https://ru.martincid.com/technology-ru/ключ-сертификата-emudhra-оказался/
        ECPrivateKeySpec specification = new ECPrivateKeySpec(BigInteger.valueOf(4), curve);
        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(specification);

        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + encoded + "\n-----END PRIVATE KEY-----\n";

        // keep existing files
        Files.writeString(output, pem, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        System.out.println("Issuer private key saved to " + output.toAbsolutePath());
    }
}
