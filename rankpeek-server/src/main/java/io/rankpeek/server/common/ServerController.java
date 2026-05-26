package io.rankpeek.server.common;

import io.rankpeek.server.auth.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/server")
public class ServerController {

    private final ServerProperties properties;
    private final ServerDiagnosticsService diagnosticsService;
    private final AuthService authService;

    public ServerController(
            ServerProperties properties,
            ServerDiagnosticsService diagnosticsService,
            AuthService authService
    ) {
        this.properties = properties;
        this.diagnosticsService = diagnosticsService;
        this.authService = authService;
    }

    @GetMapping("/health")
    public ApiResponse<ServerInfo> health() {
        return ApiResponse.success(info());
    }

    @GetMapping("/version")
    public ApiResponse<ServerInfo> version() {
        return ApiResponse.success(info());
    }

    @GetMapping("/diagnostics")
    public ApiResponse<ServerDiagnostics> diagnostics(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return ApiResponse.success(diagnosticsService.diagnostics());
    }

    private ServerInfo info() {
        return new ServerInfo("ok", properties.service(), properties.mode(), properties.version());
    }
}
