package com.tianpan.mongodbai.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${spring.data.mongodb.host}")
    private String host;

    @Value("${spring.data.mongodb.port}")
    private int port;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Value("${spring.data.mongodb.username:}")
    private String username;

    @Value("${spring.data.mongodb.password:}")
    private String password;

    @Bean
    public MongoClient mongoClient() {
        logger.info("开始创建MongoDB客户端连接");
        logger.debug("MongoDB配置 - host: {}, port: {}, database: {}", host, port, database);
        logger.debug("认证信息 - username: {}, password: {}", 
            username != null && !username.isEmpty() ? "已设置" : "未设置",
            password != null && !password.isEmpty() ? "已设置" : "未设置");
        
        String connectionString;
        
        // 如果用户名和密码为空，使用无认证连接
        if (username == null || username.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            connectionString = String.format("mongodb://%s:%d/%s", host, port, database);
            logger.info("使用无认证连接: mongodb://{}:{}/{}", host, port, database);
        } else {
            // 使用认证连接
            connectionString = String.format("mongodb://%s:%s@%s:%d/%s?authSource=admin",
                    username, "***", host, port, database);
            logger.info("使用认证连接: mongodb://***@{}:{}/{}?authSource=admin", host, port, database);
        }
        
        try {
            MongoClient client = MongoClients.create(connectionString);
            logger.info("MongoDB客户端创建成功");
            return client;
        } catch (Exception e) {
            logger.error("MongoDB客户端创建失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        logger.info("创建MongoTemplate，数据库: {}", database);
        try {
            MongoTemplate template = new MongoTemplate(mongoClient(), database);
            logger.info("MongoTemplate创建成功");
            return template;
        } catch (Exception e) {
            logger.error("MongoTemplate创建失败: {}", e.getMessage(), e);
            throw e;
        }
    }
} 