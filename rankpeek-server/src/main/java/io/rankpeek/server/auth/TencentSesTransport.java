package io.rankpeek.server.auth;

import java.net.URI;
import java.util.Map;

public interface TencentSesTransport {

    TencentSesTransportResponse post(URI uri, Map<String, String> headers, String body);
}
