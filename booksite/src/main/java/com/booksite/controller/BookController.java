package com.booksite.controller;

import com.booksite.entity.Book;
import com.booksite.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class BookController {

    @Autowired
    private BookService bookService;

    @GetMapping("/laybooks")
    public String laybooksPage(Model model) {
        model.addAttribute("bookList", bookService.listAll());
        return "laybooks";
    }

    @ResponseBody
    @PutMapping("/books/{id}")
    public Map<String, Object> updateBook(@PathVariable Integer id, @RequestBody Book book) {
        Map<String, Object> result = new HashMap<>();
        book.setId(id);
        boolean ok = bookService.update(book);
        result.put("success", ok);
        result.put("message", ok ? "修改成功" : "修改失败");
        return result;
    }

    @ResponseBody
    @PostMapping("/books")
    public Map<String, Object> addBook(@RequestBody Book book) {
        Map<String, Object> result = new HashMap<>();
        boolean ok = bookService.add(book);
        result.put("success", ok);
        result.put("message", ok ? "添加成功" : "添加失败");
        return result;
    }

    @ResponseBody
    @DeleteMapping("/books/{id}")
    public Map<String, Object> deleteBook(@PathVariable Integer id) {
        Map<String, Object> result = new HashMap<>();
        boolean ok = bookService.delete(id);
        result.put("success", ok);
        result.put("message", ok ? "删除成功" : "删除失败");
        return result;
    }
}
