package com.guide.common.context;

/**
 * 线程内上下文（如当前登录用户 id），供拦截器写入、Service 读取。
 */
public final class BaseContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        CURRENT_USER_ID.set(id);
    }

    public static Long getCurrentId() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }

    private BaseContext() {
    }
}
