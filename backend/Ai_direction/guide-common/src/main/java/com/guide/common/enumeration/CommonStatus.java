package com.guide.common.enumeration;

import lombok.Getter;

/**
 * 与库中 tinyint status 常用取值对齐：1 正常 0 停用。
 */
@Getter
public enum CommonStatus {
    DISABLED(0),
    ENABLED(1);

    private final int code;

    CommonStatus(int code) {
        this.code = code;
    }
}
