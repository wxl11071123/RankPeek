package io.rankpeek.server.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/server")
public class ServerController {

    private final ServerProperties properties;
    private final ServerDiagnosticsService diagnosticsService;

    public ServerController(ServerProperties properties, ServerDiagnosticsService diagnosticsService) {
        this.properties = properties;
        this.diagnosticsService = diagnosticsService;
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
    public ApiResponse<ServerDiagnostics> diagnostics() {
        return ApiResponse.success(diagnosticsService.diagnostics());
    }

    private ServerInfo info() {
        return new ServerInfo("ok", properties.service(), properties.mode(), properties.version());
    }
}
