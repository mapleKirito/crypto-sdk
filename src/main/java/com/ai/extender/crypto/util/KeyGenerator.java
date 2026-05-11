package com.ai.extender.crypto.util;

import java.security.NoSuchAlgorithmException;

public class KeyGenerator {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Crypto SDK - Key Generator");
        System.out.println("========================================");
        System.out.println();

        try {
            System.out.println("[1] RSA Key Pair:");
            System.out.println("----------------------------------------");
            RSAUtil.KeyPair keyPair = RSAUtil.generateKeyPair();
            System.out.println("Public Key:");
            System.out.println(keyPair.getPublicKey());
            System.out.println();
            System.out.println("Private Key:");
            System.out.println(keyPair.getPrivateKey());
            System.out.println();

            System.out.println("[2] AES Key:");
            System.out.println("----------------------------------------");
            String aesKey = AESUtil.generateKey();
            System.out.println("AES Key (Base64):");
            System.out.println(aesKey);
            System.out.println();

            System.out.println("========================================");
            System.out.println("Usage in application.yml:");
            System.out.println("========================================");
            System.out.println();
            System.out.println("  crypto:");
            System.out.println("    public-key: |");
            System.out.println("      " + keyPair.getPublicKey().replace("\n", "\n      "));
            System.out.println("    private-key: |");
            System.out.println("      " + keyPair.getPrivateKey().replace("\n", "\n      "));
            System.out.println();

        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error generating keys: " + e.getMessage());
        }
    }
}
