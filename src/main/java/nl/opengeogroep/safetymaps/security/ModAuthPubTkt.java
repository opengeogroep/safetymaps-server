package nl.opengeogroep.safetymaps.security;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import org.apache.commons.codec.binary.Base64;

/*
 * Utility to generate a signature for mod_auth_pubtkt.
 */

/**
 *
 * @author matthijsln
 */
public class ModAuthPubTkt {
    private static final String PEM_KEY_START = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_KEY_END = "-----END PRIVATE KEY-----";

    /**
     * Parse PKCS8 encoded private key. To convert a PKCS#1 key use:
     * openssl pkcs8 -topk8 -inform PEM -outform PEM -in privkey.pem -out privkey8.pem -nocrypt
     */
    public static PrivateKey getPrivateKey(String pem) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if(!pem.startsWith(PEM_KEY_START)) {
            throw new IllegalArgumentException("Expected key to start with: " + PEM_KEY_START);
        }
        while(pem.endsWith("\n")) {
            pem = pem.substring(0, pem.length() - 1);
        }
        if (!pem.endsWith(PEM_KEY_END)) {
            throw new IllegalArgumentException("Expected key to end with: " + PEM_KEY_END);
        }
        pem = pem.replace(PEM_KEY_START, "").replace(PEM_KEY_END, "");

        byte[] decoded = Base64.decodeBase64(pem);

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return kf.generatePrivate(spec);
    }

    /**
     * Sign the ticket, using algorithm supported by java.security.Signature class,
     * such as "SHA1WithRSA", "SHA256WithRSA", "SHA1WithDSA", etc.
     */
    public static String getSignature(String ticket, PrivateKey privateKey, String algorithm) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance(algorithm);
        sig.initSign(privateKey);
        sig.update(ticket.getBytes());
        return Base64.encodeBase64String(sig.sign());
    }

    /**
     * Sign the ticket, using the default SHA1.
     */
    public static String getSignature(String input, PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return getSignature(input, privateKey, "SHA1WithRSA");
    }
}
