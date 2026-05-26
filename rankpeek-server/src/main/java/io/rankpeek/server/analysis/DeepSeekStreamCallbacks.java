package io.rankpeek.server.analysis;

import io.rankpeek.server.ai.DeepSeekTokenUsage;

interface DeepSeekStreamCallbacks {

    DeepSeekStreamCallbacks NOOP = new DeepSeekStreamCallbacks() {
        @Override
        public void onSucceeded(DeepSeekTokenUsage usage) {
        }

        @Override
        public void onFailed(String errorCode, String errorMessage) {
        }
    };

    void onSucceeded(DeepSeekTokenUsage usage);

    void onFailed(String errorCode, String errorMessage);
}
