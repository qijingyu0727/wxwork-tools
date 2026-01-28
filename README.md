# 企业微信工具

企业微信智能表格管理系统，采用 Vue3 + Spring Boot 前后端分离架构。

## 技术栈

### 前端
- Vue 3
- Vite
- Vue Router
- Pinia
- Tailwind CSS
- Axios

### 后端
- Spring Boot 3.1.1
- Java 21
- MyBatis
- MySQL
- Flyway

## 项目结构

```
wxwork-tools/
├── core/
│   ├── core-backend/           # Java 后端项目
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/
│   │   │   │   │   ├── config/      # 配置类
│   │   │   │   │   ├── controller/  # 控制器
│   │   │   │   │   ├── dao/         # 数据访问层
│   │   │   │   │   ├── model/       # 数据模型
│   │   │   │   │   ├── service/     # 业务逻辑层
│   │   │   │   │   └── util/        # 工具类
│   │   │   │   └── resources/       # 资源文件
│   │   │   │       └── static/     # 前端构建产物
│   │   │   └── test/
│   │   └── pom.xml
│   └── core-frontend/          # Vue3 前端项目
│       ├── src/
│       │   ├── api/         # API 请求封装
│       │   ├── components/  # 公共组件
│       │   ├── router/      # 路由配置
│       │   ├── stores/      # Pinia 状态管理
│       │   ├── styles/      # 全局样式
│       │   ├── utils/       # 工具函数
│       │   └── views/      # 页面组件
│       ├── index.html
│       ├── package.json
│       └── vite.config.js
├── Dockerfile              # 单 jar 部署配置
├── docker-compose.yml        # 容器编排配置
└── README.md
```

## 快速开始

### 前端开发

```bash
cd core/core-frontend
npm install
npm run dev
```

前端开发服务器运行在 `http://localhost:5173`

### 后端开发

```bash
cd core/core-backend
mvn spring-boot:run
```

后端服务运行在 `http://localhost:9999`

### 一键构建部署

```bash
# 在项目根目录执行
mvn clean package

# 运行打包后的 jar（包含前后端）
java -jar core/core-backend/target/wxwork-tools-1.0.0.jar
```

执行 `mvn clean package` 时，Maven 会自动：
1. 安装 Node.js 和 npm
2. 安装前端依赖
3. 构建前端项目
4. 将前端构建产物复制到 `core/core-backend/target/classes/static/`
5. 打包后端 jar（包含前端静态资源）

### Docker 部署

```bash
docker-compose up -d
```

## 功能特性

- 企业微信扫码登录
- 智能表格创建
- 智能表格查询（支持分页）
- 管理员权限控制
- 响应式设计（支持移动端）
- 前后端一体化打包

## 环境配置

后端配置文件位于 `core/core-backend/src/main/resources/application.properties`，需要配置以下参数：

- `crop_id`: 企业微信企业ID
- `agentid`: 企业微信应用ID
- `api_get_login_qrcode_url`: 扫码登录API
- `api_get_user_info_url`: 获取用户信息API
- `api_get_user_detail_url`: 获取用户详情API
- `login_redirect_uri`: 登录回调地址
- `login_oauth2_redirect_uri`: OAuth2回调地址
- 数据库连接信息

## API 接口

### 登录相关
- `GET /wechat/work/login/generate-qrcode` - 生成扫码登录链接
- `GET /wechat/work/login/callback` - 登录回调
- `GET /wechat/work/login/check-login` - 检查登录状态
- `GET /wechat/work/login/logout` - 退出登录

### 智能表格相关
- `POST /api/doc/create` - 创建智能表格
- `GET /api/doc/search` - 查询智能表格（支持分页）

### JS-SDK 相关
- `GET /api/jsapi/get-ticket` - 获取企业 jsapi_ticket（用于企业应用鉴权）
- `GET /api/jsapi/get-agent-ticket` - 获取应用 jsapi_ticket（用于第三方应用鉴权）

## 开发说明

### 前端开发
- 前端使用 Vite 作为构建工具，支持热更新
- API 请求通过 Vite 代理转发到后端
- 使用 Pinia 进行状态管理，管理用户登录状态

### 后端开发
- 后端使用 Spring Boot 框架
- 已配置 CORS 支持跨域请求
- 使用 MyBatis 进行数据库操作
- 使用 Flyway 进行数据库版本管理
- 使用 frontend-maven-plugin 在构建时自动构建前端

## 构建部署

### 前后端一体化构建

```bash
# 在项目根目录执行
mvn clean package
```

构建过程：
1. Maven 自动下载并安装 Node.js v18.16.0
2. 安装前端依赖（npm install）
3. 构建前端项目（npm run build）
4. 将前端构建产物复制到 `core/core-backend/target/classes/static/`
5. 打包后端 jar（包含前端静态资源）

最终产物：`core/core-backend/target/wxwork-tools-1.0.0.jar`

### 运行打包后的应用

```bash
# 直接运行 jar（包含前后端）
java -jar core/core-backend/target/wxwork-tools-1.0.0.jar
```

访问地址：`http://localhost:9999`

### 前端单独构建

```bash
cd core/core-frontend
npm run build
```

构建产物位于 `core/core-frontend/dist` 目录

### 后端单独构建

```bash
cd core/core-backend
mvn clean package
```

构建产物位于 `core/core-backend/target/wxwork-tools-1.0.0.jar`
