package com.venuebooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类。
 *
 * 为什么不写 @MapperScan：项目按业务域分包（package-by-feature），
 * 每个 Mapper 接口自带 @Mapper 注解，MyBatis-Plus 自动装配会扫描
 * 主类所在包下所有 @Mapper 接口。如果用 @MapperScan 扫大包，
 * 会把模块里的 Service 接口也误注册成 Mapper。
 */
@SpringBootApplication
public class VenueBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(VenueBookingApplication.class, args);
    }
}
