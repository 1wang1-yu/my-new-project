package com.booksite.interceptor;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录拦截器：未登录用户访问受保护页面时跳转到登录页
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        HttpSession session = request.getSession();
        Object user = session.getAttribute("loginUser");

        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }
        return true;
    }
}
