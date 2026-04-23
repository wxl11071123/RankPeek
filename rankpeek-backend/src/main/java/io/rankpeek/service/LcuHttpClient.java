package io.rankpeek.service;

import io.rankpeek.exception.LcuException;
import io.rankpeek.jna.ProcessUtils;
import io.rankpeek.model.AuthInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LCU HTTP 客户端
 * 提供与英雄联盟客户端通信的 HTTP 接口
 */
@Service
@Slf4j
public class LcuHttpClient {

    private final OkHttpClient httpClient;

    @Getter
    private final ObjectMapper objectMapper;

    @Getter
    private volatile AuthInfo authInfo;
    private final ReentrantLock authLock = new ReentrantLock();

    public LcuHttpClient() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(createTrustAllSslSocketFactory(), createTrustAllTrustManager())
                .hostnameVerifier((hostname, session) -> true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 创建信任所有证书的 SSLSocketFactory
     */
    private SSLSocketFactory createTrustAllSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{createTrustAllTrustManager()}, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("创建 SSL 上下文失败", e);
        }
    }

    /**
     * 创建信任所有证书的 TrustManager
     */
    private X509TrustManager createTrustAllTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    /**
     * 获取或刷新认证信息
     */
    public AuthInfo getOrRefreshAuth() {
        authLock.lock();
        try {
            // 只在认证信息无效时才刷新
            if (authInfo == null || !authInfo.isValid()) {
                authInfo = ProcessUtils.getLcuAuthInfo();
                if (authInfo != null) {
                    log.info("LCU 认证信息已更新: port={}", authInfo.getPort());
                }
            }
            return authInfo;
        } finally {
            authLock.unlock();
        }
    }

    /**
     * 强制刷新认证信息
     */
    public void refreshAuth() {
        authLock.lock();
        try {
            authInfo = ProcessUtils.getLcuAuthInfo();
        } finally {
            authLock.unlock();
        }
    }

    /**
     * 检查 LCU 是否已连接
     */
    public boolean isConnected() {
        try {
            getOrRefreshAuth();
            return authInfo != null && authInfo.isValid();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 发送 GET 请求
     */
    public <T> T get(String uri, Class<T> responseType) {
        return executeWithRetry(() -> doGet(uri, responseType));
    }

    /**
     * 发送 POST 请求
     */
    public <T> T post(String uri, Object body, Class<T> responseType) {
        return executeWithRetry(() -> doPost(uri, body, responseType));
    }

    /**
     * 发送 PUT 请求
     */
    public <T> T put(String uri, Object body, Class<T> responseType) {
        return executeWithRetry(() -> doPut(uri, body, responseType));
    }

    /**
     * 发送 PATCH 请求
     */
    public <T> T patch(String uri, Object body, Class<T> responseType) {
        return executeWithRetry(() -> doPatch(uri, body, responseType));
    }

    /**
     * 发送 DELETE 请求
     */
    public <T> T delete(String uri, Class<T> responseType) {
        return executeWithRetry(() -> doDelete(uri, responseType));
    }

    /**
     * 获取二进制数据（如图片）
     */
    public byte[] getBytes(String uri) {
        AuthInfo auth = getOrRefreshAuth();
        if (auth == null) {
            log.warn("无法获取 LCU 认证信息");
            return null;
        }

        String url = auth.buildUrl(uri);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", auth.toBasicAuth())
                .get()
                .build();

        log.debug("LCU GET bytes: {}", uri);

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                log.warn("获取资源失败: {} - {}", uri, response.code());
                return null;
            }
            return responseBody.bytes();
        } catch (IOException e) {
            log.error("获取资源失败: {} - {}", uri, e.getMessage());
            return null;
        }
    }

    // ========== 内部实现 ==========

    private <T> T doGet(String uri, Class<T> responseType) throws IOException {
        AuthInfo auth = getOrRefreshAuth();
        if (auth == null) {
            throw new LcuException("无法获取 LCU 认证信息，请确保游戏客户端已启动");
        }

        Request request = new LcuRequestBuilder(auth)
                .uri(uri)
                .get()
                .build(null);

        log.debug("LCU GET: {}", uri);
        return executeRequest(request, responseType);
    }

    private <T> T doPost(String uri, Object body, Class<T> responseType) throws IOException {
        AuthInfo auth = getOrRefreshAuth();
        if (auth == null) {
            throw new LcuException("无法获取 LCU 认证信息，请确保游戏客户端已启动");
        }

        String jsonBody = body != null ? objectMapper.writeValueAsString(body) : "{}";

        Request request = new LcuRequestBuilder(auth)
                .uri(uri)
                .post(body)
                .build(jsonBody);

        log.debug("LCU POST: {} body={}", uri, jsonBody);
        return executeRequest(request, responseType);
    }

    private <T> T doPut(String uri, Object body, Class<T> responseType) throws IOException {
        AuthInfo auth = getOrRefreshAuth();
        if (auth == null) {
            throw new LcuException("无法获取 LCU 认证信息，请确保游戏客户端已启动");
        }

        String jsonBody = body != null ? objectMapper.writeValueAsString(body) : "{}";

        Request request = new LcuRequestBuilder(auth)
                .uri(uri)
                .put(body)
                .build(jsonBody);

        log.debug("LCU PUT: {}", uri);
        return executeRequest(request, responseType);
    }

    private <T> T doPatch(String uri, Object body, Class<T> responseType) throws IOException {
        AuthInfo auth = getOrRefreshAuth();
        if (auth == null) {
            throw new LcuException("无法获取 LCU 认证信息，请确保游戏客户端已启动");
        }

        String jsonBody = body != null ? objectMapper.writeValueAsString(body) : "{}";

        Request request = new LcuRequestBuilder(auth)
                .uri(uri)
                .patch(body)
                .build(jsonBody);

        log.debug("LCU PATCH: {}", uri);
        return executeRequest(request, responseType);
    }

    private <T> T doDelete(String uri, Class<T> responseType) throws IOException {
        AuthInfo auth = getOrRefreshAuth();
        if (auth == null) {
            throw new LcuException("无法获取 LCU 认证信息，请确保游戏客户端已启动");
        }

        Request request = new LcuRequestBuilder(auth)
                .uri(uri)
                .delete()
                .build(null);

        log.debug("LCU DELETE: {}", uri);
        return executeRequest(request, responseType);
    }

    private <T> T executeRequest(Request request, Class<T> responseType) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();

            if (!response.isSuccessful()) {
                String errorBody = responseBody != null ? responseBody.string() : "无响应体";
                throw new LcuException("LCU 请求失败: " + response.code() + " - " + errorBody);
            }

            if (responseType == Void.class || responseType == void.class) {
                return null;
            }

            if (responseBody == null) {
                return null;
            }

            String bodyString = responseBody.string();

            // 处理字符串类型返回
            if (responseType == String.class) {
                // 去除 JSON 字符串的引号
                if (bodyString.startsWith("\"") && bodyString.endsWith("\"")) {
                    bodyString = bodyString.substring(1, bodyString.length() - 1);
                }
                @SuppressWarnings("unchecked")
                T result = (T) bodyString;
                return result;
            }

            return objectMapper.readValue(bodyString, responseType);
        }
    }

    /**
     * 带重试的执行
     */
    private <T> T executeWithRetry(LcuRequest<T> request) {
        Exception lastException = null;

        for (int i = 0; i < 2; i++) {
            try {
                return request.execute();
            } catch (IOException e) {
                lastException = e;
                log.warn("LCU 请求 IO 异常，刷新认证后重试({}/2): {}", i + 1, e.getMessage());
                refreshAuth();
            } catch (LcuException e) {
                lastException = e;
                if (e.getMessage() != null && e.getMessage().contains("LCU 请求失败")) {
                    log.warn("LCU 请求返回错误，刷新认证后重试({}/2): {}", i + 1, e.getMessage());
                    refreshAuth();
                } else {
                    throw e;
                }
            }
        }

        throw new LcuException("LCU 请求失败(已重试): " + (lastException != null ? lastException.getMessage() : "未知错误"), lastException);
    }

    @FunctionalInterface
    private interface LcuRequest<T> {
        T execute() throws IOException;
    }

}
