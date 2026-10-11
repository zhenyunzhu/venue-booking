# ===== 多阶段构建：阶段一用 Maven + JDK 编译打包 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先只拷 pom 下载依赖，利用 Docker 层缓存：源码变更不用重新拉依赖
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# ===== 阶段二：只带 JRE 的精简运行镜像（不含编译工具链） =====
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/venue-booking-1.0.0.jar app.jar
EXPOSE 8080
# 容器内建议让 JVM 感知容器资源限制
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
