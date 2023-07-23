package com.hzx.distributedlock.controller;

import com.hzx.distributedlock.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @auther hzx
 * @create 2023/7/18 20:37
 */
@RestController
public class ConsumeController {

    @Autowired
    BookService bookService;

    @RequestMapping("/deBook")
    public String deBook(){
        bookService.deBook();
        return "Book减1成功！";
    }




}
