package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data // 如果使用了 Lombok
@TableName("`Group`") // <--- 添加这个注解，注意表名用反引号包裹
public class Group {

    @TableId(type = IdType.ASSIGN_UUID) // 或者你使用的其他主键策略
    private String groupId;

    private String name;

    private Integer creatorId; // 确认类型与 Account ID 一致

    private Date create_at; // 确认字段名与数据库列名 create_at 匹配，MyBatis-Plus 默认驼峰转下划线

    // 其他字段、构造函数、getter/setter...
}
