package com.io.appioweb.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptTool {
    public static void main(String[] args) {
        var enc = new BCryptPasswordEncoder();
        String plainProp = System.getProperty("plain");
        String hashProp = System.getProperty("hash");
        if (plainProp != null && hashProp != null) {
            System.out.println(enc.matches(plainProp, hashProp));
            return;
        }

        if (args.length >= 2) {
            String plain = args[0];
            String hash = args[1];
            System.out.println(enc.matches(plain, hash));
            return;
        }

        String plain = args.length > 0 ? args[0] : "Admin@123";
        System.out.println(enc.encode(plain));
    }
}
