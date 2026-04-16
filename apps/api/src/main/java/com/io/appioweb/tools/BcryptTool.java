package com.io.appioweb.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptTool {
    public static void main(String[] args) {
        String plain = args.length > 0 ? args[0] : "Admin@123";
        var enc = new BCryptPasswordEncoder();
        System.out.println(enc.encode(plain));
    }
}
