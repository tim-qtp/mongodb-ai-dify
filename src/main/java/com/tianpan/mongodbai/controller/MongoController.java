package com.tianpan.mongodbai.controller;

import com.tianpan.mongodbai.dto.QueryRequest;
import com.tianpan.mongodbai.dto.QueryResponse;
import com.tianpan.mongodbai.service.MongoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mongo")
@CrossOrigin(origins = "*")
public class MongoController {

    private static final Logger logger = LoggerFactory.getLogger(MongoController.class);

    @Autowired
    private MongoService mongoService;

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody(required = false) QueryRequest request) {
        logger.info("收到查询请求: {}", request);
        
        try {
            String queryString = null;
            
            // 如果请求体为空，尝试从请求参数获取
            if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
                // 尝试从请求体直接读取字符串
                return ResponseEntity.badRequest()
                    .body(QueryResponse.error("请提供查询语句"));
            }
            
            queryString = request.getQuery().trim();
            
            if (queryString.isEmpty()) {
                logger.error("查询语句为空");
                return ResponseEntity.badRequest()
                    .body(QueryResponse.error("查询语句不能为空"));
            }
            
            logger.info("执行查询: {}", queryString);
            String result = mongoService.executeQuery(queryString);
            logger.info("查询成功，结果长度: {}", result.length());
            
            return ResponseEntity.ok(QueryResponse.success(result));
            
        } catch (Exception e) {
            logger.error("查询失败", e);
            return ResponseEntity.status(500)
                .body(QueryResponse.error("查询失败: " + e.getMessage()));
        }
    }

    @PostMapping("/query/raw")
    public ResponseEntity<QueryResponse> queryRaw(@RequestBody String queryString) {
        logger.info("收到原始查询请求: {}", queryString);
        
        try {
            if (queryString == null || queryString.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(QueryResponse.error("查询语句不能为空"));
            }
            
            String trimmedQuery = queryString.trim();
            logger.info("执行原始查询: {}", trimmedQuery);
            String result = mongoService.executeQuery(trimmedQuery);
            logger.info("查询成功，结果长度: {}", result.length());
            
            return ResponseEntity.ok(QueryResponse.success(result));
            
        } catch (Exception e) {
            logger.error("查询失败", e);
            return ResponseEntity.status(500)
                .body(QueryResponse.error("查询失败: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        logger.info("健康检查请求");
        return ResponseEntity.ok("MongoDB服务运行正常");
    }

    /**
     * 获取今日告警的系统名称列表
     */
    @GetMapping("/today-alarm-systems")
    public ResponseEntity<Map<String, Object>> getTodayAlarmSystems() {
        logger.info("获取今日告警系统请求");
        
        try {
            List<String> systems = mongoService.getTodayAlarmSystemNames();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("systems", systems);
            response.put("count", systems.size());
            response.put("message", "成功获取今日告警系统");
            
            logger.info("成功获取今日告警系统，数量: {}", systems.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取今日告警系统失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取今日告警系统失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
} 