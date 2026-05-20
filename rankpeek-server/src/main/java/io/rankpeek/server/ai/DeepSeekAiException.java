package io.rankpeek.server.ai;

public class DeepSeekAiException extends RuntimeException {

    public DeepSeekAiException(String message) {
        super(message);
    }

    public DeepSeekAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
