# MongoDB查询工具

这是一个基于Spring Boot的MongoDB Web服务，允许前端传入MongoDB查询语句，后端执行并返回结果。

## 功能特性

- 支持MongoDB查询语句执行
- 提供Web界面进行查询
- 支持常见的查询操作（count、find等）
- RESTful API接口
- 支持告警系统数据查询

## 技术栈

- Spring Boot 2.7.18
- Spring Data MongoDB
- Maven
- HTML/CSS/JavaScript

## 快速开始

### 1. 启动应用

```bash
mvn spring-boot:run
```

### 2. 访问Web界面

打开浏览器访问：http://localhost:8848

### 3. API接口

#### POST /api/mongo/query

执行MongoDB查询语句

**请求体：**
```json
{
    "query": "db.alarm_info.count()"
}
```

**响应：**
```json
{
    "success": true,
    "result": "{\"type\":\"count\",\"data\":42}",
    "message": "查询成功"
}
```

#### POST /api/mongo/query/raw

执行原始查询语句

**请求体：**
```
db.alarm_info.count()
```

#### GET /api/mongo/health

健康检查接口

#### GET /api/mongo/today-alarm-systems

获取今日告警系统列表

## 支持的查询语句

- `db.alarm_info.count()` - 统计告警信息数量
- `db.alarm_info.find()` - 查询所有告警信息
- `db.alarm_info.find({})` - 查询所有告警信息（空条件）
- `db.alarm_info.find({field: value})` - 条件查询
- `db.alarm_itsc.find()` - 查询告警关联信息
- `db.alarm_ignore.find()` - 查询告警忽略信息

## 配置说明

数据库连接配置在 `application.properties` 中：

### 无认证连接（默认）
```properties
spring.data.mongodb.host=127.0.0.1
spring.data.mongodb.port=27017
spring.data.mongodb.database=detect
```

### 认证连接（可选）
如果需要用户名密码认证，请取消注释并填写正确的凭据：
```properties
spring.data.mongodb.host=127.0.0.1
spring.data.mongodb.port=27017
spring.data.mongodb.database=detect
spring.data.mongodb.username=your_username
spring.data.mongodb.password=your_password
spring.data.mongodb.authentication-database=admin
```

## 项目结构

```
src/
├── main/
│   ├── java/com/tianpan/mongodbai/
│   │   ├── config/MongoConfig.java          # MongoDB配置
│   │   ├── controller/MongoController.java  # REST控制器
│   │   ├── dto/                             # 数据传输对象
│   │   │   ├── QueryRequest.java            # 查询请求DTO
│   │   │   └── QueryResponse.java           # 查询响应DTO
│   │   ├── service/MongoService.java        # MongoDB业务逻辑
│   │   └── MongodbAiApplication.java        # 启动类
│   └── resources/
│       ├── static/index.html                # Web界面
│       └── application.properties           # 配置文件
```

## 使用示例

1. 在Web界面输入查询语句：`db.alarm_info.count()`
2. 点击"执行查询"按钮
3. 查看返回结果

## 注意事项

- 确保MongoDB服务可访问
- 查询语句需要符合MongoDB语法
- 目前主要支持count和find操作
- 默认连接detect数据库的告警相关集合 