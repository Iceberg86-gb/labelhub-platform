package com.labelhub.api.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_roles")
public class UserRoleEntity {

    private Long userId;
    private Long roleId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
