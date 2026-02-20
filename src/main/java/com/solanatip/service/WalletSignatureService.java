package com.solanatip.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;

@Service
@Slf4j
public class WalletSignatureService {

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    /**
     * Verify that a message was signed by the owner of the given Solana wallet address.
     *
     * @param walletAddress Base58-encoded Solana public key
     * @param message       The original message that was signed
     * @param signatureB58  Base58-encoded Ed25519 signature (as returned by Phantom)
     */
    public boolean verifySignature(String walletAddress, String message, String signatureB58) {
        try {
            byte[] publicKeyBytes = base58Decode(walletAddress);
            byte[] signatureBytes = base58Decode(signatureB58);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

            // Build Ed25519 public key from raw bytes
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");

            // Ed25519 public keys are 32 bytes - determine the EdECPoint
            // The y-coordinate is the key bytes in little-endian, MSB of last byte is the x sign bit
            boolean xOdd = (publicKeyBytes[publicKeyBytes.length - 1] & 0x80) != 0;
            byte[] yBytes = publicKeyBytes.clone();
            yBytes[yBytes.length - 1] &= 0x7F; // clear the sign bit

            // Reverse to little-endian for BigInteger
            reverse(yBytes);
            java.math.BigInteger y = new java.math.BigInteger(1, yBytes);

            EdECPoint point = new EdECPoint(xOdd, y);
            EdECPublicKeySpec pubKeySpec = new EdECPublicKeySpec(NamedParameterSpec.ED25519, point);
            PublicKey publicKey = keyFactory.generatePublic(pubKeySpec);

            // Verify signature
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(messageBytes);

            return sig.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Wallet signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ========== Base58 Decode ==========

    private static byte[] base58Decode(String input) {
        if (input == null || input.isEmpty()) {
            return new byte[0];
        }

        // Count leading '1's (which represent leading zero bytes)
        int leadingZeros = 0;
        for (int i = 0; i < input.length() && input.charAt(i) == '1'; i++) {
            leadingZeros++;
        }

        // Decode
        byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;

        for (int i = leadingZeros; i < input.length(); i++) {
            int digit = ALPHABET.indexOf(input.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base58 character: " + input.charAt(i));
            }

            int carry = digit;
            for (int j = decoded.length - 1; j >= outputStart || carry != 0; j--) {
                carry += 58 * (decoded[j] & 0xFF);
                decoded[j] = (byte) (carry % 256);
                carry /= 256;
                if (j <= outputStart) {
                    outputStart = j;
                }
            }
        }

        // Build result with leading zeros
        byte[] result = new byte[leadingZeros + (decoded.length - outputStart)];
        System.arraycopy(decoded, outputStart, result, leadingZeros, decoded.length - outputStart);
        return result;
    }

    private static void reverse(byte[] arr) {
        for (int i = 0, j = arr.length - 1; i < j; i++, j--) {
            byte tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }
}