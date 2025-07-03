package com.tianpan.mongodbai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MongodbAiApplication {

    private static final Logger logger = LoggerFactory.getLogger(MongodbAiApplication.class);

    public static void main(String[] args) {
        logger.info("启动MongoDB查询工具应用...");
        logger.info("应用版本: 1.0.0");
        logger.info("Java版本: {}", System.getProperty("java.version"));
        logger.info("操作系统: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        
        try {
            SpringApplication.run(MongodbAiApplication.class, args);
            logger.info("MongoDB查询工具应用启动成功！");
            logger.info("访问地址: http://localhost:8848");
        } catch (Exception e) {
            logger.error("应用启动失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

}
