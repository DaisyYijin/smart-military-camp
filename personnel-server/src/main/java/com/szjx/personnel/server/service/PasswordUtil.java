package com.szjx.personnel.server.service;

import com.szjx.personnel.server.model.Personnel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码工具:PBKDF2WithHmacSHA256(JDK8 内置,无需额外依赖)
 */
@Service
public class PasswordUtil {

    private static final int ITERATIONS = 100000;
    private static final int KEY_LENGTH = 256;
    private static final int LEGACY_ITERATIONS = 20000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成 迭代数$盐$哈希
     */
    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH);
        return ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verify(String password, String stored) {
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length == 2) {
            // 旧格式:盐$哈希(20000 次迭代)
            return verifyWith(password, parts[0], parts[1], LEGACY_ITERATIONS);
        }
        if (parts.length == 3) {
            // 新格式:迭代数$盐$哈希
            try {
                int iterations = Integer.parseInt(parts[0]);
                return verifyWith(password, parts[1], parts[2], iterations);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean verifyWith(String password, String saltB64, String hashB64, int iterations) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltB64);
            byte[] expected = Base64.getDecoder().decode(hashB64);
            byte[] actual = pbkdf2(password, salt, iterations, expected.length * 8);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
