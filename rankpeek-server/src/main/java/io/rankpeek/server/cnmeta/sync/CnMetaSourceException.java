package io.rankpeek.server.cnmeta.sync;

class CnMetaSourceException extends RuntimeException {

    private final Integer httpStatus;
    private final boolean stopSignal;

    CnMetaSourceException(String message) {
        this(message, null, false);
    }

    CnMetaSourceException(String message, Integer httpStatus) {
        this(message, httpStatus, false);
    }

    private CnMetaSourceException(String message, Integer httpStatus, boolean stopSignal) {
        super(message);
        this.httpStatus = httpStatus;
        this.stopSignal = stopSignal;
    }

    static CnMetaSourceException stopSignal(String message) {
        return new CnMetaSourceException(message, null, true);
    }

    Integer httpStatus() {
        return httpStatus;
    }

    boolean stopSignal() {
        return stopSignal;
    }
}
