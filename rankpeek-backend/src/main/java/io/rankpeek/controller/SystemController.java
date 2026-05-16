package io.rankpeek.controller;

import io.rankpeek.model.ApiResponse;
import io.rankpeek.service.SystemShutdownService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemShutdownService shutdownService;

    @PostMapping("/shutdown")
    public ApiResponse<Map<String, String>> shutdown(HttpServletRequest request) {
        if (!isLocalRequest(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "shutdown is only available from localhost");
        }

        boolean accepted = shutdownService.requestShutdown();
        return ApiResponse.success(Map.of(
                "status", accepted ? "shutting-down" : "already-requested"
        ));
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }

        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (Exception ignored) {
            return "127.0.0.1".equals(remoteAddress) || "::1".equals(remoteAddress);
        }
    }
}
