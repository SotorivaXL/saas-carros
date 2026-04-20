package com.io.appioweb.adapters.web.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Profile("local")
@RestController
@RequestMapping("/dev")
public class DevToolsController {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final DevShowcaseSeedService showcaseSeedService;

    public DevToolsController(DevShowcaseSeedService showcaseSeedService) {
        this.showcaseSeedService = showcaseSeedService;
    }

    @GetMapping("/bcrypt")
    public String bcrypt(@RequestParam String plain) {
        return encoder.encode(plain);
    }

    @PostMapping("/showcase/seed")
    public ResponseEntity<DevShowcaseSeedService.ShowcaseSeedResult> seedShowcase(
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String email
    ) {
        return ResponseEntity.ok(showcaseSeedService.seedShowcase(companyId, email));
    }
}
