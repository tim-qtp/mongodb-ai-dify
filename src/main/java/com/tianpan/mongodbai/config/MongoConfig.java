package com.tianpan.mongodbai.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

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
        String connectionString;
        
        // 如果用户名和密码为空，使用无认证连接
        if (username == null || username.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            connectionString = String.format("mongodb://%s:%d/%s", host, port, database);
        } else {
            // 使用认证连接
            connectionString = String.format("mongodb://%s:%s@%s:%d/%s?authSource=admin",
                    username, password, host, port, database);
        }
        
        return MongoClients.create(connectionString);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), database);
    }
} 