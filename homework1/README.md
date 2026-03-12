# 分布式系统作业一：高并发读基础架构搭建

本项目实现了基于 Docker 和 Nginx 的高并发读基础架构，包含后端服务集群、MySQL 数据库和 Nginx 负载均衡与动静分离配置。

## 项目结构

```
作业一/
├── docker-compose.yml       # 容器编排文件
├── Dockerfile               # 后端服务构建文件
├── pom.xml                  # Maven 项目配置
├── src/                     # Java 源代码
│   ├── main/java/com/example/demo/
│   │   ├── DemoApplication.java  # 启动类
│   │   └── TestController.java   # 测试接口
│   └── main/resources/
│       └── application.yml       # 应用配置
├── nginx/
│   └── conf/
│       └── nginx.conf       # Nginx 配置文件（负载均衡、动静分离）
├── static/                  # 静态资源文件
│   ├── index.html           # 测试首页
│   ├── style.css            # 样式文件
│   ├── script.js            # 脚本文件
│   └── image.svg            # 示例图片
└── mysql-init/
    └── init.sql             # 数据库初始化脚本
```

## 服务连接与配置信息

本部分提供了您需要连接到各服务的详细信息，方便使用数据库管理工具（如 Navicat, DBeaver, MySQL Workbench）或 API 测试工具（如 Postman）进行连接。

### 1. MySQL 数据库连接信息

如果您需要从宿主机（Windows）连接到 Docker 中的 MySQL 数据库，请使用以下信息：

| 配置项 | 值 | 说明 |
| :--- | :--- | :--- |
| **主机 (Host)** | `127.0.0.1` 或 `localhost` | 宿主机地址 |
| **端口 (Port)** | `3307` | **注意：** 宿主机端口已改为 3307 以避免与本地 MySQL 冲突 |
| **用户名 (Username)** | `user` | 普通应用用户 |
| **密码 (Password)** | `password` | 普通用户密码 |
| **Root 用户名** | `root` | 超级管理员 |
| **Root 密码** | `root` | 管理员密码 |
| **数据库 (Database)** | `test_db` | 默认创建的测试数据库 |
| **连接 URL (JDBC)** | `jdbc:mysql://localhost:3307/test_db` | Java 应用连接字符串（宿主机运行应用时使用） |

> **提示**：如果是在 Docker 容器内部（如 app1, app2）连接数据库，主机名请使用 `mysql`，端口使用 `3306`。

### 2. Web 服务访问地址

| 服务 | 访问地址 | 说明 |
| :--- | :--- | :--- |
| **Nginx (入口)** | [http://localhost](http://localhost) | 统一入口，负载均衡 + 动静分离 |
| **App1 (后端实例1)** | [http://localhost:8081/api/test](http://localhost:8081/api/test) | 直接访问后端实例 1 |
| **App2 (后端实例2)** | [http://localhost:8082/api/test](http://localhost:8082/api/test) | 直接访问后端实例 2 |

### 3. Nginx 配置说明

Nginx 配置文件位于 `./nginx/conf/nginx.conf`。修改后需执行 `docker-compose restart nginx` 生效。

- **负载均衡策略**：默认轮询 (Round Robin)。可在配置文件中切换为加权轮询或 IP Hash。
- **动静分离规则**：
    - `/static/` 开头的路径 -> 直接访问静态文件
    - `.html`, `.css`, `.js`, `.png`, `.svg` 等后缀 -> 直接访问静态文件
    - `/api/` 开头的路径 -> 转发给后端服务集群

## 快速开始

### 前置条件
- 已安装 Docker 和 Docker Compose
- 确保端口 80, 8081, 8082, 3306 未被占用

### 启动服务

在项目根目录下运行以下命令：

```bash
docker-compose up -d --build
```

该命令将：
1. 构建后端 Java 应用镜像（基于 Maven 和 JDK 17）
2. 启动 MySQL 8.0 数据库并初始化数据
3. 启动两个后端服务实例 (app1, app2)
4. 启动 Nginx 并挂载配置文件

### 停止服务

```bash
docker-compose down
```

## 验证步骤

### 1. 访问静态页面（动静分离验证）
在浏览器中访问 [http://localhost](http://localhost)
- 你应该能看到 "Load Balancing Test" 页面。
- 页面包含样式（CSS 生效）、图片（SVG 生效）。
- 点击 "Show Time" 按钮，弹出当前时间（JS 生效）。
- 这些资源均由 Nginx 直接提供，不经过后端。

### 2. 验证负载均衡
在页面上点击 **"Test Backend"** 按钮多次。
- 观察 "Backend Instance" 和 "Server Port" 的变化。
- 默认配置为 **轮询 (Round Robin)**，你应该看到 app1 (8081) 和 app2 (8082) 交替响应。

### 3. 验证数据库
MySQL 数据持久化在 `D:\学习资料\distributed_system\distributed_data\mysql`。
可以使用数据库管理工具连接 `localhost:3307`：
- 用户名：`user`
- 密码：`password`
- 数据库：`test_db`
- 表：`test_table` (包含初始数据)

## 负载均衡配置说明

在 `nginx/conf/nginx.conf` 中，你可以修改 `location /api/` 下的 `proxy_pass` 来切换算法：

- `http://backend_rr`：轮询（默认）
- `http://backend_wrr`：加权轮询 (app1: 3, app2: 1)
- `http://backend_iphash`：IP 哈希

修改后需重启 Nginx 容器生效：
```bash
docker-compose restart nginx
```
