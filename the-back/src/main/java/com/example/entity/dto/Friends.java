package com.example.entity.dto;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.util.Date;
import java.sql.Timestamp;

@Data
@TableName("friends")
public class Friends {
    private int id;
    @TableField("the_first_user_id")//一定要注意，mybatis-plus对应的字段在java中不能使用下划线命名法，需要使用驼峰命名法，否则无法映射
    private int theFirstUserId;
    @TableField("the_second_user_id")
    private int theSecondUserId;
    @TableField("status")
    private Status status;
    @TableField("created_at")
    private Timestamp createdAt;

    public enum Status {
        requested,
        accepted,
        rejected,
        deleted
    }
}
