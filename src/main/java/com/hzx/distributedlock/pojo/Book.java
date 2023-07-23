package com.hzx.distributedlock.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * @auther hzx
 * @create 2023/7/18 20:38
 */
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@TableName("book")
public class Book {
    private Integer id;
    private String name;
    private int count;
    private int version;
}
