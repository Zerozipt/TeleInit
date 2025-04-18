package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("Group")
public class Group {
    private String groupId;
    private String name;
    private String creatorId;
    private Date create_at;
}
