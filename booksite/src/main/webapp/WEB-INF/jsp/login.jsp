<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>管理员登录</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { background: #f0f2f5; font-family: "Microsoft YaHei", sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
        .login-box { background: #fff; padding: 40px 36px; border-radius: 12px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); width: 380px; }
        h2 { text-align: center; margin-bottom: 28px; color: #333; }
        .field { margin-bottom: 18px; }
        .field label { display: block; margin-bottom: 6px; font-size: 14px; color: #666; }
        .field input { width: 100%; padding: 10px 14px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; }
        .field input:focus { outline: none; border-color: #409eff; }
        .btn { width: 100%; padding: 12px; background: #409eff; color: #fff; border: none; border-radius: 6px; font-size: 16px; cursor: pointer; }
        .btn:hover { background: #337ecc; }
        .error { color: #e74c3c; text-align: center; margin-bottom: 14px; font-size: 14px; }
    </style>
</head>
<body>
    <div class="login-box">
        <h2>图书管理系统</h2>
        <c:if test="${not empty error}">
            <p class="error">${error}</p>
        </c:if>
        <form action="${pageContext.request.contextPath}/login/doLogin" method="post">
            <div class="field">
                <label>用户名</label>
                <input type="text" name="username" placeholder="请输入用户名" required />
            </div>
            <div class="field">
                <label>密码</label>
                <input type="password" name="password" placeholder="请输入密码" required />
            </div>
            <button type="submit" class="btn">登 录</button>
        </form>
    </div>
</body>
</html>
