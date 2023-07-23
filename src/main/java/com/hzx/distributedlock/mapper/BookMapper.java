package com.hzx.distributedlock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzx.distributedlock.pojo.Book;
import org.apache.ibatis.annotations.Mapper;

/**
 * @auther hzx
 * @create 2023/7/18 20:45
 */
@Mapper
public interface BookMapper extends BaseMapper<Book> {
}
