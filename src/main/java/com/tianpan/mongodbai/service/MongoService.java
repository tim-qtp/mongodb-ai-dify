package com.tianpan.mongodbai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MongoService {

    private static final Logger logger = LoggerFactory.getLogger(MongoService.class);

    @Autowired
    private MongoClient mongoClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 安全地从Document中获取字符串值
     */
    private String getStringValue(Document doc, String key, String defaultValue) {
        logger.debug("获取Document字段值 - key: {}, defaultValue: {}", key, defaultValue);
        
        Object value = doc.get(key);
        if (value == null) {
            logger.debug("字段值为null，返回默认值: {}", defaultValue);
            return defaultValue;
        }
        
        logger.debug("字段值类型: {}, 值: {}", value.getClass().getSimpleName(), value);
        
        // 处理不同类型的值
        if (value instanceof java.util.Date) {
            // 如果是Date类型，格式化为字符串
            java.util.Date date = (java.util.Date) value;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String formattedDate = formatter.format(date);
            logger.debug("Date类型格式化结果: {}", formattedDate);
            return formattedDate;
        } else if (value instanceof String) {
            // 如果是字符串类型，尝试格式化时间
            String strValue = (String) value;
            if (strValue.matches(".*\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*\\d{1,2}:\\d{2}:\\d{2}.*")) {
                // 如果字符串包含时间格式，尝试格式化
                String formattedTime = formatTimeForExcel(strValue);
                logger.debug("时间字符串格式化结果: {} -> {}", strValue, formattedTime);
                return formattedTime;
            }
            logger.debug("字符串类型，直接返回: {}", strValue);
            return strValue;
        } else {
            // 其他类型转换为字符串
            String stringValue = value.toString();
            logger.debug("其他类型转换为字符串: {} -> {}", value.getClass().getSimpleName(), stringValue);
            return stringValue;
        }
    }

    public String executeQuery(String query) {
        logger.info("开始执行MongoDB查询: {}", query);
        
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            logger.debug("连接到数据库: detect");
            
            MongoCollection<Document> collection = database.getCollection("alarm_info");
            logger.debug("使用集合: alarm_info");
            
            // 解析查询语句
            logger.debug("开始解析查询语句");
            QueryParser parser = new QueryParser(query);
            QueryResult result = parser.execute(collection);
            
            logger.debug("查询执行完成，结果类型: {}", result.getType());
            
            // 将结果转换为JSON字符串
            String jsonResult = objectMapper.writeValueAsString(result);
            logger.info("查询执行成功，结果长度: {}", jsonResult.length());
            
            return jsonResult;
            
        } catch (JsonProcessingException e) {
            logger.error("JSON序列化失败", e);
            throw new RuntimeException("JSON序列化失败", e);
        } catch (Exception e) {
            logger.error("查询执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据itsc_id列表查询alarm_itsc表中的case_exec_id
     * @param itscIds itsc_id列表
     * @return case_exec_id列表
     */
    public List<String> getCaseExecIdsByItscIds(List<String> itscIds) {
        logger.info("根据itsc_id列表查询case_exec_id，itscIds数量: {}", itscIds.size());
        logger.debug("itscIds: {}", itscIds);
        
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_itsc");
            
            // 构建查询条件：itsc_id在给定的列表中
            Document filter = new Document("itsc_id", new Document("$in", itscIds));
            logger.debug("查询条件: {}", filter.toJson());
            
            // 执行查询
            List<String> caseExecIds = new ArrayList<>();
            collection.find(filter).forEach(doc -> {
                String caseExecId = doc.getString("case_exec_id");
                if (caseExecId != null) {
                    caseExecIds.add(caseExecId);
                    logger.debug("找到case_exec_id: {}", caseExecId);
                } else {
                    logger.warn("文档中case_exec_id字段为null: {}", doc.toJson());
                }
            });
            
            logger.info("查询完成，找到case_exec_id数量: {}", caseExecIds.size());
            return caseExecIds;
            
        } catch (Exception e) {
            logger.error("查询alarm_itsc表失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询alarm_itsc表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据itsc_id列表查询alarm_itsc表中的case_exec_id，返回关联关系
     * @param itscIds itsc_id列表
     * @return Map<itsc_id, List<case_exec_id>> 关联关系
     */
    public Map<String, List<String>> getCaseExecIdsMappingByItscIds(List<String> itscIds) {
        logger.info("根据itsc_id列表查询case_exec_id关联关系，itscIds数量: {}", itscIds.size());
        logger.debug("itscIds: {}", itscIds);
        
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_itsc");
            
            // 构建查询条件：itsc_id在给定的列表中
            Document filter = new Document("itsc_id", new Document("$in", itscIds));
            logger.debug("查询条件: {}", filter.toJson());
            
            // 执行查询并构建关联关系
            Map<String, List<String>> mapping = new HashMap<>();
            collection.find(filter).forEach(doc -> {
                String itscId = doc.getString("itsc_id");
                String caseExecId = doc.getString("case_exec_id");
                if (itscId != null && caseExecId != null) {
                    mapping.computeIfAbsent(itscId, k -> new ArrayList<>()).add(caseExecId);
                    logger.debug("建立关联关系: {} -> {}", itscId, caseExecId);
                } else {
                    logger.warn("文档中字段为null - itsc_id: {}, case_exec_id: {}", itscId, caseExecId);
                }
            });
            
            logger.info("查询完成，关联关系数量: {}", mapping.size());
            logger.debug("关联关系详情: {}", mapping);
            return mapping;
            
        } catch (Exception e) {
            logger.error("查询alarm_itsc表失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询alarm_itsc表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据case_exec_id查询alarm_info表中的详细信息
     * @param caseExecId case_exec_id
     * @return alarm_info表的完整记录
     */
    public Document getAlarmInfoByCaseExecId(String caseExecId) {
        logger.info("根据case_exec_id查询alarm_info详细信息: {}", caseExecId);
        
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_info");
            
            // 用alert_id字段查
            Document filter = new Document("alert_id", caseExecId);
            logger.debug("查询条件: {}", filter.toJson());
            
            // 执行查询
            Document result = collection.find(filter).first();
            
            if (result == null) {
                logger.warn("未找到alert_id为 {} 的告警信息", caseExecId);
                throw new RuntimeException("未找到alert_id为 " + caseExecId + " 的告警信息");
            }
            
            logger.info("成功找到告警信息，文档ID: {}", result.get("_id"));
            logger.debug("告警信息内容: {}", result.toJson());
            return result;
            
        } catch (Exception e) {
            logger.error("查询alarm_info表失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询alarm_info表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据case_exec_id（alert_id）查询alarm_ignore表中的失败原因和反应时间
     * @param caseExecId case_exec_id
     * @return Map，包含failed和reaction_time字段
     */
    public Map<String, String> getAlarmIgnoreFailedAndReactionTime(String caseExecId) {
        logger.info("查询alarm_ignore表，alert_id: {}", caseExecId);
        
        Map<String, String> resultMap = new HashMap<>();
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_ignore");
            Document filter = new Document("alert_id", caseExecId);
            
            logger.debug("查询条件: {}", filter.toJson());
            
            Document result = collection.find(filter).first();
            
            if (result == null) {
                logger.warn("未找到alert_id为 {} 的记录", caseExecId);
                // 尝试查看表中有哪些记录
                logger.debug("alarm_ignore表中的前5条记录:");
                collection.find().limit(5).forEach(doc -> {
                    logger.debug("  alert_id: {}, reaction_time: {}", 
                        doc.getString("alert_id"), doc.get("reaction_time"));
                });
                resultMap.put("failed", null);
                resultMap.put("reaction_time", null);
                return resultMap;
            }
            
            logger.info("找到记录，文档ID: {}", result.get("_id"));
            logger.debug("记录内容: {}", result.toJson());
            logger.debug("reaction_time字段值: {}", result.get("reaction_time"));
            logger.debug("reaction_time字段类型: {}", 
                result.get("reaction_time") != null ? result.get("reaction_time").getClass().getName() : "null");
            
            resultMap.put("failed", getStringValue(result, "failed", null));
            String rawReactionTime = getStringValue(result, "reaction_time", null);
            // 强制格式化 reaction_time，确保格式正确
            if (rawReactionTime != null && !rawReactionTime.trim().isEmpty()) {
                String formattedTime = formatTimeForExcel(rawReactionTime);
                resultMap.put("reaction_time", formattedTime);
                logger.debug("格式化reaction_time: {} -> {}", rawReactionTime, formattedTime);
            } else {
                resultMap.put("reaction_time", rawReactionTime);
            }
            
            logger.debug("处理后的reaction_time: {}", resultMap.get("reaction_time"));
            
            return resultMap;
        } catch (Exception e) {
            logger.error("查询alarm_ignore表失败: {}", e.getMessage(), e);
            e.printStackTrace();
            throw new RuntimeException("查询alarm_ignore表失败: " + e.getMessage(), e);
        }
    }

    // 保留原有的单字段方法以兼容老代码
    public String getAlarmIgnoreFailedReason(String caseExecId) {
        logger.debug("获取告警忽略失败原因，caseExecId: {}", caseExecId);
        Map<String, String> map = getAlarmIgnoreFailedAndReactionTime(caseExecId);
        String failedReason = map.get("failed");
        logger.debug("获取到的失败原因: {}", failedReason);
        return failedReason;
    }
    
    public String getAlarmIgnoreReactionTime(String caseExecId) {
        logger.debug("获取告警忽略反应时间，caseExecId: {}", caseExecId);
        Map<String, String> map = getAlarmIgnoreFailedAndReactionTime(caseExecId);
        String reactionTime = map.get("reaction_time");
        logger.debug("获取到的反应时间: {}", reactionTime);
        return reactionTime;
    }

    // 修改 formatTimeForExcel 方法，添加对特殊格式的支持
    private String formatTimeForExcel(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return "";
        }
        
        logger.debug("formatTimeForExcel 输入: {}", timeStr);
        
        try {
            // 如果已经是目标格式，直接返回
            if (timeStr.matches("\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{2}$")) {
                logger.debug("formatTimeForExcel 已经是目标格式，直接返回: {}", timeStr);
                return timeStr;
            }
            
            // 尝试多种时间格式
            java.time.LocalDateTime dateTime = null;
            
            // 0. 尝试特殊格式 "yyyy-MM-dd H.mm:ss.SSS"（数据库实际格式）
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd H.mm:ss.SSS");
                dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                logger.debug("formatTimeForExcel 解析成功 (特殊格式): {}", timeStr);
            } catch (Exception e0) {
                // 1. 尝试 "yyyy-MM-dd HH:mm:ss.SSS" 格式
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                    dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                    logger.debug("formatTimeForExcel 解析成功 (SSS): {}", timeStr);
                } catch (Exception e1) {
                    // 2. 尝试 "yyyy-MM-dd HH:mm:ss" 格式
                    try {
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                        logger.debug("formatTimeForExcel 解析成功 (ss): {}", timeStr);
                    } catch (Exception e2) {
                        // 3. 尝试 "yyyy-MM-dd HH:mm" 格式
                        try {
                            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                            dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                            logger.debug("formatTimeForExcel 解析成功 (mm): {}", timeStr);
                        } catch (Exception e3) {
                            // 4. 尝试Date对象的字符串表示（如 "Mon Jun 30 23:30:44 CST 2025"）
                            if (timeStr.contains("CST") || timeStr.contains("GMT") || timeStr.contains("UTC")) {
                                try {
                                    java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.ENGLISH);
                                    java.util.Date date = inputFormat.parse(timeStr);
                                    dateTime = date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                                    logger.debug("formatTimeForExcel 解析成功 (CST): {}", timeStr);
                                } catch (Exception e4) {
                                    logger.debug("formatTimeForExcel 所有格式都失败，返回原字符串: {}", timeStr);
                                    return timeStr;
                                }
                            } else {
                                logger.debug("formatTimeForExcel 所有格式都失败，返回原字符串: {}", timeStr);
                                return timeStr;
                            }
                        }
                    }
                }
            }
            
            // 格式化为 "yyyy/M/d H:mm" 格式（小时不补零）
            if (dateTime != null) {
                java.time.format.DateTimeFormatter outputFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d H:mm");
                String result = dateTime.format(outputFormatter);
                logger.debug("formatTimeForExcel 格式化结果: {}", result);
                return result;
            } else {
                logger.debug("formatTimeForExcel dateTime为null，返回原字符串: {}", timeStr);
                return timeStr;
            }
        } catch (Exception e) {
            logger.debug("formatTimeForExcel 异常，返回原字符串: {}，异常: {}", timeStr, e.getMessage());
            return timeStr;
        }
    }

    /**
     * 获取今日告警的系统名称列表
     * @return 系统名称列表
     */
    public List<String> getTodayAlarmSystemNames() {
        logger.info("开始获取今日告警系统名称列表");
        
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_info");
            logger.debug("连接到数据库: detect，集合: alarm_info");
            
            // 获取今日的起止时间（东八区）
            java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
            java.time.LocalDate today = java.time.LocalDate.now(zone);
            java.time.LocalDateTime todayStart = today.atStartOfDay();
            java.time.LocalDateTime todayEnd = today.atTime(23, 59, 59, 999000000);
            
            logger.debug("今日时间范围: {} 到 {}", todayStart, todayEnd);
            
            // 转换为UTC时间
            java.util.Date startDate = java.util.Date.from(todayStart.atZone(zone).toInstant());
            java.util.Date endDate = java.util.Date.from(todayEnd.atZone(zone).toInstant());
            
            // 构建查询条件：今日的告警记录
            Document filter = new Document();
            filter.put("end_time", new Document("$gte", startDate).append("$lte", endDate));
            filter.put("alarm_type", "business"); // 只查询业务故障
            
            logger.debug("查询条件: {}", filter.toJson());
            
            // 执行查询并获取唯一的系统名称
            List<String> systemNames = new ArrayList<>();
            AtomicInteger processedCount = new AtomicInteger();
            collection.find(filter).forEach(doc -> {
                processedCount.getAndIncrement();
                String systemName = getStringValue(doc, "system_name", "");
                if (!systemName.isEmpty() && !systemNames.contains(systemName)) {
                    systemNames.add(systemName);
                    logger.debug("添加系统名称: {}", systemName);
                }
            });
            
            logger.info("查询完成，处理文档数量: {}，找到系统名称数量: {}", processedCount, systemNames.size());
            logger.debug("系统名称列表: {}", systemNames);
            
            return systemNames;
            
        } catch (Exception e) {
            logger.error("获取今日告警系统名称失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取今日告警系统名称失败: " + e.getMessage(), e);
        }
    }
    
    // 内部类用于解析和执行MongoDB查询
    private static class QueryParser {
        private final String query;
        private static final Logger logger = LoggerFactory.getLogger(QueryParser.class);
        
        public QueryParser(String query) {
            this.query = query.trim();
            logger.debug("创建QueryParser，查询语句: {}", this.query);
        }
        
        public QueryResult execute(MongoCollection<Document> collection) {
            logger.debug("开始执行查询: {}", query);
            
            if (query.toLowerCase().startsWith("db.alarm_info.count()")) {
                logger.debug("执行count查询");
                long count = collection.countDocuments();
                logger.info("count查询完成，结果: {}", count);
                return new QueryResult("count", count);
            } else if (query.toLowerCase().startsWith("db.alarm_info.find(")) {
                logger.debug("执行find查询");
                // 解析复杂的find查询
                return parseFindQuery(collection);
            } else {
                logger.error("不支持的查询语句: {}", query);
                throw new IllegalArgumentException("不支持的查询语句: " + query);
            }
        }
        
        private QueryResult parseFindQuery(MongoCollection<Document> collection) {
            logger.debug("开始解析find查询: {}", query);
            
            // 简化解析逻辑，直接处理常见的查询模式
            String queryLower = query.toLowerCase();
            
            // 提取查询条件
            Document filter = new Document();
            int findStart = query.indexOf("find(") + 5;
            int findEnd = query.indexOf(")", findStart);
            
            logger.debug("查询条件位置: findStart={}, findEnd={}", findStart, findEnd);
            
            if (findStart > 4 && findEnd > findStart) {
                String conditions = query.substring(findStart, findEnd).trim();
                logger.debug("提取的查询条件: '{}'", conditions);
                
                if (!conditions.isEmpty() && !conditions.equals("{}")) {
                    try {
                        // 尝试直接解析
                        filter = Document.parse(conditions);
                        logger.debug("直接解析查询条件成功: {}", filter.toJson());
                    } catch (Exception e1) {
                        logger.debug("直接解析失败，尝试替换单引号: {}", e1.getMessage());
                        try {
                            // 如果失败，尝试替换单引号为双引号
                            String normalizedConditions = conditions.replace("'", "\"");
                            filter = Document.parse(normalizedConditions);
                            logger.debug("替换单引号后解析成功: {}", filter.toJson());
                        } catch (Exception e2) {
                            logger.error("解析查询条件失败: {}", conditions);
                            throw new IllegalArgumentException("无效的查询条件: " + conditions + "。请使用双引号或单引号。");
                        }
                    }
                } else {
                    logger.debug("查询条件为空或{}，使用空过滤器");
                }
            }
            
            // 构建基础查询
            com.mongodb.client.FindIterable<Document> findIterable = collection.find(filter);
            logger.debug("构建基础查询，过滤器: {}", filter.toJson());
            
            // 处理链式操作
            String remainingQuery = query.substring(findEnd + 1);
            logger.debug("剩余查询部分: '{}'", remainingQuery);
            
            // 处理sort
            if (remainingQuery.contains(".sort(")) {
                logger.debug("检测到sort操作");
                int sortStart = remainingQuery.indexOf(".sort(") + 6;
                int sortEnd = remainingQuery.indexOf(")", sortStart);
                if (sortStart > 5 && sortEnd > sortStart) {
                    String sortCondition = remainingQuery.substring(sortStart, sortEnd).trim();
                    logger.debug("sort条件: '{}'", sortCondition);
                    try {
                        Document sortDoc = Document.parse(sortCondition);
                        findIterable = findIterable.sort(sortDoc);
                        logger.debug("sort解析成功: {}", sortDoc.toJson());
                    } catch (Exception e1) {
                        logger.debug("sort直接解析失败，尝试替换单引号: {}", e1.getMessage());
                        try {
                            // 尝试替换单引号为双引号
                            String normalizedSort = sortCondition.replace("'", "\"");
                            Document sortDoc = Document.parse(normalizedSort);
                            findIterable = findIterable.sort(sortDoc);
                            logger.debug("sort替换单引号后解析成功: {}", sortDoc.toJson());
                        } catch (Exception e2) {
                            logger.error("sort解析失败: {}", sortCondition);
                            throw new IllegalArgumentException("无效的sort条件: " + sortCondition + "。请使用双引号或单引号。");
                        }
                    }
                }
            }
            
            // 处理limit
            if (remainingQuery.contains(".limit(")) {
                logger.debug("检测到limit操作");
                int limitStart = remainingQuery.indexOf(".limit(") + 7;
                int limitEnd = remainingQuery.indexOf(")", limitStart);
                if (limitStart > 6 && limitEnd > limitStart) {
                    String limitValue = remainingQuery.substring(limitStart, limitEnd).trim();
                    logger.debug("limit值: '{}'", limitValue);
                    try {
                        int limit = Integer.parseInt(limitValue);
                        findIterable = findIterable.limit(limit);
                        logger.debug("limit设置成功: {}", limit);
                    } catch (Exception e) {
                        logger.error("limit解析失败: {}", limitValue);
                        throw new IllegalArgumentException("无效的limit值: " + limitValue);
                    }
                }
            }
            
            // 执行查询
            logger.debug("开始执行find查询");
            List<Document> documents = new ArrayList<>();
            findIterable.into(documents);
            
            logger.info("find查询完成，返回文档数量: {}", documents.size());
            logger.debug("查询结果示例: {}", documents.isEmpty() ? "无结果" : documents.get(0).toJson());
            
            return new QueryResult("find", documents);
        }
    }
    
    // 查询结果类
    private static class QueryResult {
        private String type;
        private Object data;
        
        public QueryResult(String type, Object data) {
            this.type = type;
            this.data = data;
        }
        
        public String getType() { return type; }
        public Object getData() { return data; }
    }

    public Map<String, Object> getFlowInstanceById(String flowInstanceId) {
        // 查询工单基本信息
        Map<String, Object> instance = new HashMap<>();
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            // 这里假设你有PostgreSQL或其他方式查工单基本信息，以下为伪代码：
            // 实际应根据你的业务调整
            // 这里只查MongoDB的alarm_itsc和alarm_info
            instance.put("flow_instance_id", flowInstanceId);
            instance.put("ticket_name", "单工单导出");
            // 查alarmIds
            List<String> alarmIds = new ArrayList<>();
            MongoCollection<Document> itscColl = database.getCollection("alarm_itsc");
            Document filter = new Document("itsc_id", flowInstanceId);
            itscColl.find(filter).forEach(doc -> {
                String caseExecId = getStringValue(doc, "case_exec_id", "");
                if (!caseExecId.isEmpty()) alarmIds.add(caseExecId);
            });
            instance.put("alarmIds", alarmIds);
            instance.put("alarmCount", alarmIds.size());
            return instance;
        } catch (Exception e) {
            logger.error("getFlowInstanceById error: {}", e.getMessage(), e);
            return null;
        }
    }
}
 