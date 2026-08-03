package com.sq.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.sq.common.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Date;

/**
 * @author tzt
 * @apiNote Mybatis Plus 配置
 */
@EnableTransactionManagement(proxyTargetClass = true)
@Configuration
// 指定要扫描的Mapper类的包的路径
@MapperScan("com.sq.**.mapper")
@Slf4j
public class MybatisPlusConfig {
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                Date now = new Date();
                this.strictInsertFill(metaObject, "createTime", Date.class, now);
                this.strictInsertFill(metaObject, "updateTime", Date.class, now);
                this.strictInsertFill(metaObject, "delFlag", String.class, "0");
                try {
                    String username = SecurityUtils.getUsername();
                    this.strictInsertFill(metaObject, "createBy", String.class, username);
                    this.strictInsertFill(metaObject, "updateBy", String.class, username);
                } catch (Exception e) {
                    log.error("获取登录用户失败，跳过 createBy/updateBy 自动填充",e);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
                try {
                    String username = SecurityUtils.getUsername();
                    this.strictUpdateFill(metaObject, "updateBy", String.class, username);
                } catch (Exception e) {
                    log.error("获取登录用户失败，跳过 updateBy 自动填充",e);
                }
            }
        };
    }


    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件：mp PaginationInnerInterceptor 与 pagehelper 6.1.1 双栈共存（方案 C 最终决策）。
        // 历史背景：旧方案在 jsqlparser 4.6 / pagehelper 5.3.3 下，mp PaginationInnerInterceptor
        //     与 BlockAttackInnerInterceptor 调用 jsqlparser 4.7+ API（ParenthesedSelect /
        //     parseStatements(String,ExecutorService,Consumer)）必报 NoSuchMethodError，
        //     被迫禁用 mp 的 SQL 解析类插件。
        // 现在方案：BOM 锁 jsqlparser 4.9 + pagehelper-spring-boot-starter 2.1.1（含 pagehelper 6.1.1），
        //     pagehelper 6.x 已基于 jsqlparser 4.7+ API 重写 CountSqlParser，与 mp 全套
        //     InnerInterceptor 在 4.9 上共存。
        // 团队规范：分页方案双栈共存——简单 BaseMapper 查询用 mp Page<>（PaginationInnerInterceptor 自动改写 LIMIT），
        //     复杂自定义 SQL/XML 用 PageHelper.startPage() + PageInfo。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.DM));
        // 乐观锁插件
        interceptor.addInnerInterceptor(optimisticLockerInnerInterceptor());
        // 阻断插件
        interceptor.addInnerInterceptor(blockAttackInnerInterceptor());
        return interceptor;
    }

    /**
     * 乐观锁插件 https://baomidou.com/guide/interceptor-optimistic-locker.html
     */
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        return new OptimisticLockerInnerInterceptor();
    }

    /**
     * 如果是对全表的删除或更新操作，就会终止该操作 https://baomidou.com/guide/interceptor-block-attack.html
     */
    public BlockAttackInnerInterceptor blockAttackInnerInterceptor() {
        return new BlockAttackInnerInterceptor();
    }
}
