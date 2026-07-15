package com.booksite.entity;

public class Book {
    private Integer id;
    private String bookName;
    private String author;
    private String publish;
    private Double price;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getBookName() { return bookName; }
    public void setBookName(String bookName) { this.bookName = bookName; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getPublish() { return publish; }
    public void setPublish(String publish) { this.publish = publish; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
