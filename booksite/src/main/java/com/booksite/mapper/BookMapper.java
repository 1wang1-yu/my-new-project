package com.booksite.mapper;

import com.booksite.entity.Book;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BookMapper {
    List<Book> findAll();
    Book findById(Integer id);
    int update(Book book);
    int insert(Book book);
    int deleteById(Integer id);
}
