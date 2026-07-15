package com.booksite.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            // 删除旧表(VARCHAR列)，重建为NVARCHAR以支持中文
            jdbcTemplate.execute("IF OBJECT_ID('dbo.books', 'U') IS NOT NULL DROP TABLE books");
            jdbcTemplate.execute(
                "CREATE TABLE books (" +
                "  id INT IDENTITY(1,1) PRIMARY KEY," +
                "  bookName NVARCHAR(100) NOT NULL," +
                "  author NVARCHAR(50) NOT NULL," +
                "  publish NVARCHAR(255)," +
                "  price FLOAT" +
                ")"
            );

            // 插入10条数据
            {
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'SpringBoot', N'张三', N'清华大学出版社', 49.00)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'MyBatis', N'李四', N'人民邮电出版社', null)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'Python', N'李四', N'人民邮电出版社', 35.00)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'数据库原理', N'王五', N'清华大学出版社', 45.00)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'Java', N'王五', null, 39.00)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'数据结构', N'李四', null, null)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'人工智能', N'小明', N'人民邮电出版社', 39.00)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'大数据技术', N'小明', N'电子工业出版社', null)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'C语言编程', N'小明', null, 36.00)");
                jdbcTemplate.execute("INSERT INTO books (bookName, author, publish, price) VALUES (N'计算机网络', N'赵六', null, 40.00)");
                System.out.println(">>> 已插入 10 条书籍数据");
            }
        } catch (Exception e) {
            System.err.println(">>> 初始化失败: " + e.getMessage());
        }
    }
}
