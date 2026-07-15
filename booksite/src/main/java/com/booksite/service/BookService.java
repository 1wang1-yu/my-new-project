package com.booksite.service;

import com.booksite.entity.Book;

import java.util.List;

public interface BookService {
    List<Book> listAll();
    Book getById(Integer id);
    boolean update(Book book);
    boolean add(Book book);
    boolean delete(Integer id);
}
