package com.example.entity.vo.response;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class GroupDetailResponse {
    private String groupId;
    private String name;
    private Integer creatorId;
    private String creatorName;
    private Date createAt;
    private Integer memberCount;
    private List<GroupMemberResponse> members;
}
