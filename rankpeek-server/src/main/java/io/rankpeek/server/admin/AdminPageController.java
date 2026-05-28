package io.rankpeek.server.admin;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    private static final Resource ADMIN_PAGE = new ClassPathResource("static/admin/index.html");

    @GetMapping({"/admin", "/admin/"})
    public ResponseEntity<Resource> adminPage() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(ADMIN_PAGE);
    }
}
