package com.io.appioweb.adapters.web.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Profile("local")
@RestController
@RequestMapping("/dev")
public class DevToolsController {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @GetMapping("/bcrypt")
    public String bcrypt(@RequestParam String plain) {
        return encoder.encode(plain);
    }
}
