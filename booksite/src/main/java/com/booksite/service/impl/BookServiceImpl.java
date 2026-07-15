package com.booksite.service.impl;

import com.booksite.entity.Book;
import com.booksite.mapper.BookMapper;
import com.booksite.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookServiceImpl implements BookService {

    @Autowired
    private BookMapper bookMapper;

    @Override
    public List<Book> listAll() {
        return bookMapper.findAll();
    }

    @Override
    public Book getById(Integer id) {
        return bookMapper.findById(id);
    }

    @Override
    public boolean update(Book book) {
        return bookMapper.update(book) > 0;
    }

    @Override
    public boolean add(Book book) {
        return bookMapper.insert(book) > 0;
    }

    @Override
    public boolean delete(Integer id) {
        return bookMapper.deleteById(id) > 0;
    }
}
