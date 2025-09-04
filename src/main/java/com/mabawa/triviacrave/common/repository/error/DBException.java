package com.mabawa.triviacrave.common.repository.error;

public class DBException extends RuntimeException {
    public DBException(String message) {
        super(message);
    }
}
