package com.tianpan.mongodbai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MongoService {

    @Autowired
    private MongoClient mongoClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 安全地从Document中获取字符串值
     */
    private String getStringValue(Document doc, String key, String defaultValue) {
        Object value = doc.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        // 处理不同类型的值
        if (value instanceof java.util.Date) {
            // 如果是Date类型，格式化为字符串
            java.util.Date date = (java.util.Date) value;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            return formatter.format(date);
        } else if (value instanceof String) {
            // 如果是字符串类型，尝试格式化时间
            String strValue = (String) value;
            if (strValue.matches(".*\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*\\d{1,2}:\\d{2}:\\d{2}.*")) {
                // 如果字符串包含时间格式，尝试格式化
                return formatTimeForExcel(strValue);
            }
            return strValue;
        } else {
            // 其他类型转换为字符串
            return value.toString();
        }
    }

    public String executeQuery(String query) {
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_info");
            
            // 解析查询语句
            QueryParser parser = new QueryParser(query);
            QueryResult result = parser.execute(collection);
            
            // 将结果转换为JSON字符串
            return objectMapper.writeValueAsString(result);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败", e);
        } catch (Exception e) {
            throw new RuntimeException("查询执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据itsc_id列表查询alarm_itsc表中的case_exec_id
     * @param itscIds itsc_id列表
     * @return case_exec_id列表
     */
    public List<String> getCaseExecIdsByItscIds(List<String> itscIds) {
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_itsc");
            
            // 构建查询条件：itsc_id在给定的列表中
            Document filter = new Document("itsc_id", new Document("$in", itscIds));
            
            // 执行查询
            List<String> caseExecIds = new ArrayList<>();
            collection.find(filter).forEach(doc -> {
                String caseExecId = doc.getString("case_exec_id");
                if (caseExecId != null) {
                    caseExecIds.add(caseExecId);
                }
            });
            
            return caseExecIds;
            
        } catch (Exception e) {
            throw new RuntimeException("查询alarm_itsc表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据itsc_id列表查询alarm_itsc表中的case_exec_id，返回关联关系
     * @param itscIds itsc_id列表
     * @return Map<itsc_id, List<case_exec_id>> 关联关系
     */
    public Map<String, List<String>> getCaseExecIdsMappingByItscIds(List<String> itscIds) {
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_itsc");
            
            // 构建查询条件：itsc_id在给定的列表中
            Document filter = new Document("itsc_id", new Document("$in", itscIds));
            
            // 执行查询并构建关联关系
            Map<String, List<String>> mapping = new HashMap<>();
            collection.find(filter).forEach(doc -> {
                String itscId = doc.getString("itsc_id");
                String caseExecId = doc.getString("case_exec_id");
                if (itscId != null && caseExecId != null) {
                    mapping.computeIfAbsent(itscId, k -> new ArrayList<>()).add(caseExecId);
                }
            });
            
            return mapping;
            
        } catch (Exception e) {
            throw new RuntimeException("查询alarm_itsc表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据case_exec_id查询alarm_info表中的详细信息
     * @param caseExecId case_exec_id
     * @return alarm_info表的完整记录
     */
    public Document getAlarmInfoByCaseExecId(String caseExecId) {
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_info");
            
            // 用alert_id字段查
            Document filter = new Document("alert_id", caseExecId);
            
            // 执行查询
            Document result = collection.find(filter).first();
            
            if (result == null) {
                throw new RuntimeException("未找到alert_id为 " + caseExecId + " 的告警信息");
            }
            
            return result;
            
        } catch (Exception e) {
            throw new RuntimeException("查询alarm_info表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据case_exec_id（alert_id）查询alarm_ignore表中的失败原因和反应时间
     * @param caseExecId case_exec_id
     * @return Map，包含failed和reaction_time字段
     */
    public Map<String, String> getAlarmIgnoreFailedAndReactionTime(String caseExecId) {
        Map<String, String> resultMap = new HashMap<>();
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_ignore");
            Document filter = new Document("alert_id", caseExecId);
            
            System.out.println("查询alarm_ignore表，alert_id: " + caseExecId);
            System.out.println("查询条件: " + filter.toJson());
            
            Document result = collection.find(filter).first();
            
            if (result == null) {
                System.out.println("未找到alert_id为 " + caseExecId + " 的记录");
                // 尝试查看表中有哪些记录
                System.out.println("alarm_ignore表中的所有alert_id:");
                collection.find().limit(5).forEach(doc -> {
                    System.out.println("  alert_id: " + doc.getString("alert_id") + ", reaction_time: " + doc.get("reaction_time"));
                });
                resultMap.put("failed", null);
                resultMap.put("reaction_time", null);
                return resultMap;
            }
            
            System.out.println("找到记录: " + result.toJson());
            System.out.println("reaction_time字段值: " + result.get("reaction_time"));
            System.out.println("reaction_time字段类型: " + (result.get("reaction_time") != null ? result.get("reaction_time").getClass().getName() : "null"));
            
            resultMap.put("failed", getStringValue(result, "failed", null));
            String rawReactionTime = getStringValue(result, "reaction_time", null);
            // 强制格式化 reaction_time，确保格式正确
            if (rawReactionTime != null && !rawReactionTime.trim().isEmpty()) {
                resultMap.put("reaction_time", formatTimeForExcel(rawReactionTime));
            } else {
                resultMap.put("reaction_time", rawReactionTime);
            }
            
            System.out.println("处理后的reaction_time: " + resultMap.get("reaction_time"));
            
            return resultMap;
        } catch (Exception e) {
            System.err.println("查询alarm_ignore表失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("查询alarm_ignore表失败: " + e.getMessage(), e);
        }
    }

    // 保留原有的单字段方法以兼容老代码
    public String getAlarmIgnoreFailedReason(String caseExecId) {
        Map<String, String> map = getAlarmIgnoreFailedAndReactionTime(caseExecId);
        return map.get("failed");
    }
    public String getAlarmIgnoreReactionTime(String caseExecId) {
        Map<String, String> map = getAlarmIgnoreFailedAndReactionTime(caseExecId);
        return map.get("reaction_time");
    }

    // 修改 formatTimeForExcel 方法，添加对特殊格式的支持
    private String formatTimeForExcel(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return "";
        }
        
        System.out.println("formatTimeForExcel 输入: " + timeStr);
        
        try {
            // 如果已经是目标格式，直接返回
            if (timeStr.matches("\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{2}$")) {
                System.out.println("formatTimeForExcel 已经是目标格式，直接返回: " + timeStr);
                return timeStr;
            }
            
            // 尝试多种时间格式
            java.time.LocalDateTime dateTime = null;
            
            // 0. 尝试特殊格式 "yyyy-MM-dd H.mm:ss.SSS"（数据库实际格式）
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd H.mm:ss.SSS");
                dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                System.out.println("formatTimeForExcel 解析成功 (特殊格式): " + timeStr);
            } catch (Exception e0) {
                // 1. 尝试 "yyyy-MM-dd HH:mm:ss.SSS" 格式
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                    dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                    System.out.println("formatTimeForExcel 解析成功 (SSS): " + timeStr);
                } catch (Exception e1) {
                    // 2. 尝试 "yyyy-MM-dd HH:mm:ss" 格式
                    try {
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                        System.out.println("formatTimeForExcel 解析成功 (ss): " + timeStr);
                    } catch (Exception e2) {
                        // 3. 尝试 "yyyy-MM-dd HH:mm" 格式
                        try {
                            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                            dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                            System.out.println("formatTimeForExcel 解析成功 (mm): " + timeStr);
                        } catch (Exception e3) {
                            // 4. 尝试Date对象的字符串表示（如 "Mon Jun 30 23:30:44 CST 2025"）
                            if (timeStr.contains("CST") || timeStr.contains("GMT") || timeStr.contains("UTC")) {
                                try {
                                    java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.ENGLISH);
                                    java.util.Date date = inputFormat.parse(timeStr);
                                    dateTime = date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                                    System.out.println("formatTimeForExcel 解析成功 (CST): " + timeStr);
                                } catch (Exception e4) {
                                    System.out.println("formatTimeForExcel 所有格式都失败，返回原字符串: " + timeStr);
                                    return timeStr;
                                }
                            } else {
                                System.out.println("formatTimeForExcel 所有格式都失败，返回原字符串: " + timeStr);
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
                System.out.println("formatTimeForExcel 格式化结果: " + result);
                return result;
            } else {
                System.out.println("formatTimeForExcel dateTime为null，返回原字符串: " + timeStr);
                return timeStr;
            }
        } catch (Exception e) {
            System.out.println("formatTimeForExcel 异常，返回原字符串: " + timeStr + ", 异常: " + e.getMessage());
            return timeStr;
        }
    }

    /**
     * 获取今日告警的系统名称列表
     * @return 系统名称列表
     */
    public List<String> getTodayAlarmSystemNames() {
        try {
            MongoDatabase database = mongoClient.getDatabase("detect");
            MongoCollection<Document> collection = database.getCollection("alarm_info");
            
            // 获取今日的起止时间（东八区）
            java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
            java.time.LocalDate today = java.time.LocalDate.now(zone);
            java.time.LocalDateTime todayStart = today.atStartOfDay();
            java.time.LocalDateTime todayEnd = today.atTime(23, 59, 59, 999000000);
            
            // 转换为UTC时间
            java.util.Date startDate = java.util.Date.from(todayStart.atZone(zone).toInstant());
            java.util.Date endDate = java.util.Date.from(todayEnd.atZone(zone).toInstant());
            
            // 构建查询条件：今日的告警记录
            Document filter = new Document();
            filter.put("end_time", new Document("$gte", startDate).append("$lte", endDate));
            filter.put("alarm_type", "business"); // 只查询业务故障
            
            // 执行查询并获取唯一的系统名称
            List<String> systemNames = new ArrayList<>();
            collection.find(filter).forEach(doc -> {
                String systemName = getStringValue(doc, "system_name", "");
                if (!systemName.isEmpty() && !systemNames.contains(systemName)) {
                    systemNames.add(systemName);
                }
            });
            
            return systemNames;
            
        } catch (Exception e) {
            throw new RuntimeException("获取今日告警系统名称失败: " + e.getMessage(), e);
        }
    }
    
    // 内部类用于解析和执行MongoDB查询
    private static class QueryParser {
        private final String query;
        
        public QueryParser(String query) {
            this.query = query.trim();
        }
        
        public QueryResult execute(MongoCollection<Document> collection) {
            if (query.toLowerCase().startsWith("db.alarm_info.count()")) {
                long count = collection.countDocuments();
                return new QueryResult("count", count);
            } else if (query.toLowerCase().startsWith("db.alarm_info.find(")) {
                // 解析复杂的find查询
                return parseFindQuery(collection);
            } else {
                throw new IllegalArgumentException("不支持的查询语句: " + query);
            }
        }
        
        private QueryResult parseFindQuery(MongoCollection<Document> collection) {
            // 简化解析逻辑，直接处理常见的查询模式
            String queryLower = query.toLowerCase();
            
            // 提取查询条件
            Document filter = new Document();
            int findStart = query.indexOf("find(") + 5;
            int findEnd = query.indexOf(")", findStart);
            
            if (findStart > 4 && findEnd > findStart) {
                String conditions = query.substring(findStart, findEnd).trim();
                if (!conditions.isEmpty() && !conditions.equals("{}")) {
                    try {
                        // 尝试直接解析
                        filter = Document.parse(conditions);
                    } catch (Exception e1) {
                        try {
                            // 如果失败，尝试替换单引号为双引号
                            String normalizedConditions = conditions.replace("'", "\"");
                            filter = Document.parse(normalizedConditions);
                        } catch (Exception e2) {
                            throw new IllegalArgumentException("无效的查询条件: " + conditions + "。请使用双引号或单引号。");
                        }
                    }
                }
            }
            
            // 构建基础查询
            com.mongodb.client.FindIterable<Document> findIterable = collection.find(filter);
            
            // 处理链式操作
            String remainingQuery = query.substring(findEnd + 1);
            
            // 处理sort
            if (remainingQuery.contains(".sort(")) {
                int sortStart = remainingQuery.indexOf(".sort(") + 6;
                int sortEnd = remainingQuery.indexOf(")", sortStart);
                if (sortStart > 5 && sortEnd > sortStart) {
                    String sortCondition = remainingQuery.substring(sortStart, sortEnd).trim();
                    try {
                        Document sortDoc = Document.parse(sortCondition);
                        findIterable = findIterable.sort(sortDoc);
                    } catch (Exception e1) {
                        try {
                            // 尝试替换单引号为双引号
                            String normalizedSort = sortCondition.replace("'", "\"");
                            Document sortDoc = Document.parse(normalizedSort);
                            findIterable = findIterable.sort(sortDoc);
                        } catch (Exception e2) {
                            throw new IllegalArgumentException("无效的sort条件: " + sortCondition + "。请使用双引号或单引号。");
                        }
                    }
                }
            }
            
            // 处理limit
            if (remainingQuery.contains(".limit(")) {
                int limitStart = remainingQuery.indexOf(".limit(") + 7;
                int limitEnd = remainingQuery.indexOf(")", limitStart);
                if (limitStart > 6 && limitEnd > limitStart) {
                    String limitValue = remainingQuery.substring(limitStart, limitEnd).trim();
                    try {
                        int limit = Integer.parseInt(limitValue);
                        findIterable = findIterable.limit(limit);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("无效的limit值: " + limitValue);
                    }
                }
            }
            
            // 执行查询
            List<Document> documents = new ArrayList<>();
            findIterable.into(documents);
            
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
            System.err.println("getFlowInstanceById error: " + e.getMessage());
            return null;
        }
    }
}
 