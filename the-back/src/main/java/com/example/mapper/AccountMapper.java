package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.dto.Account;
import org.apache.ibatis.annotations.Mapper;

@Mapper
//BaseMapper是MyBatis-Plus提供的一个通用Mapper接口，提供了一些常用的数据库操作方法
//例如：insert、delete、update、select等
//通过继承BaseMapper接口，可以快速实现数据库操作
public interface AccountMapper extends BaseMapper<Account> {

}
