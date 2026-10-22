
package enjami;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {

    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public record PasswordHash(String salt, String hash) {
    }

    public static PasswordHash hashPassword(String password)
            throws GeneralSecurityException {

        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException(
                    "Password must contain at least 12 characters.");
        }

        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        byte[] hash = deriveKey(password.toCharArray(), salt);

        return new PasswordHash(
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash));
    }

    public static boolean verifyPassword(
            String password, String storedSalt, String storedHash)
            throws GeneralSecurityException {

        if (password == null || storedSalt == null || storedHash == null) {
            return false;
        }

        try {
            byte[] salt = Base64.getDecoder().decode(storedSalt);
            byte[] expectedHash = Base64.getDecoder().decode(storedHash);

            if (salt.length != SALT_BYTES || expectedHash.length != KEY_BITS / 8) {
                return false;
            }

            byte[] actualHash = deriveKey(password.toCharArray(), salt);

            return MessageDigest.isEqual(expectedHash, actualHash);

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] deriveKey(char[] password, byte[] salt)
            throws GeneralSecurityException {

        PBEKeySpec spec = new PBEKeySpec(
                password, salt, ITERATIONS, KEY_BITS);

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return factory.generateSecret(spec).getEncoded();

        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(password, '\0');
        }
    }
}


