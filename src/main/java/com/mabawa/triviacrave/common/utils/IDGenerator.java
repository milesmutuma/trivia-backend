package com.mabawa.triviacrave.common.utils;

import io.hypersistence.tsid.TSID;

public class IDGenerator {
    public static Long generateId() {
        return TSID.Factory.getTsid().toLong();
    }
}
