/**
 * MythosForge 后端网关（Spring Boot 3）：对外 REST，对内调用 writer-python，PostgreSQL 为事实源。
 *
 * <p><b>数据库怎么访问</b>：依赖 {@code spring-boot-starter-data-jpa}，即 <b>Spring Data JPA + Hibernate</b>。
 * 实体类使用 {@code jakarta.persistence.*} 注解映射表；仓库接口继承 {@link org.springframework.data.jpa.repository.JpaRepository}
 * 由框架生成 CRUD/派生查询实现。<b>未使用</b> MyBatis / MyBatis-Plus。
 *
 * <p><b>表结构从哪来</b>：{@code src/main/resources/db/migration} 下的 Flyway SQL（{@code V1__}…）在启动时校验/迁移；
 * JPA 多数场景为 {@code ddl-auto: validate}，即实体与库表由迁移脚本对齐，而不是 Hibernate 自动建表。
 *
 * <p><b>分包大致含义</b>：
 * {@code project} — 项目与工作区；{@code genre} — 题材合同与 Writer 题材流水线；
 * {@code story} — 初始化小说与快照；{@code chapter} — 章纲实体；{@code writer} — 出站调用 Writer；
 * {@code common} — 横切（CORS、健康检查）。
 */
package com.mythosforge;
