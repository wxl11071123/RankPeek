package io.rankpeek.server.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/server")
public class ServerController {

    private final ServerProperties properties;

    public ServerController(ServerProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public ApiResponse<ServerInfo> health() {
        return ApiResponse.success(info());
    }

    @GetMapping("/version")
    public ApiResponse<ServerInfo> version() {
        return ApiResponse.success(info());
    }

    private ServerInfo info() {
        return new ServerInfo("ok", properties.service(), properties.mode(), properties.version());
    }
}
