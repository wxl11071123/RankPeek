package io.rankpeek.server.auth;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class PasswordResetPageController {

    @GetMapping(value = "/password-reset/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> passwordResetPage(@PathVariable String token) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/password-reset/index.html");
        String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }
}
