package com.venuebooking.module.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表实体。
 *
 * 三个值得注意的注解：
 * 1. @TableField(select = false)：默认 SELECT 不查 password 字段，
 *    从源头防止"顺手把密码散列序列化给前端"的泄露事故；
 *    登录等需要密码的场景，用 QueryWrapper 显式 select 再查。
 * 2. @TableLogic：配合 yml 里 logic-delete-field 配置，MP 生成的
 *    查询自动追加 deleted=0，删除变成 UPDATE deleted=1。
 * 3. @TableId(type = IdType.AUTO)：用户 ID 用数据库自增；
 *    而订单号这种业务单据号，M4 会用雪花算法另行生成（有序、分布式安全）。
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 散列，任何接口都不得直接返回 */
    @TableField(select = false)
    private String password;

    private String nickname;

    private String phone;

    /** 1-普通用户 2-场馆商家 3-核销员 4-平台管理员 */
    private Integer role;

    /** 1-正常 0-禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
