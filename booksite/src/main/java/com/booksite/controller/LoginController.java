package com.booksite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;

@Controller
public class LoginController {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "1234";

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login/doLogin")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        if (ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password)) {
            session.setAttribute("loginUser", username);
            return "redirect:/laybooks";
        }
        redirectAttributes.addFlashAttribute("error", "用户名或密码错误");
        return "redirect:/login";
    }
}
