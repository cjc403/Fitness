# FitChain 开发交付约定

- 用户要求每次优化都同时更新本地项目和云服务器；只有本地验证通过后才能发布，发布后必须线上验收，失败保留旧版本或回滚。
- 生产地址见 deploy/linux/README.md。部署前备份 PostgreSQL、旧后端和旧前端。密码与API Key只能保存在受保护配置中，禁止输出或提交。
- 不覆盖已有会员、订单、账号和密码；不追溯自动扣课；Flyway已有迁移不修改，使用新版本。
- 本项目当前不是Git仓库。修改前保留源文件备份；未经明确要求不初始化仓库、不删除用户文件。
- 验证：fitness-backend 执行 mvn test package；fitness-frontend 执行 npm run build；新财务/权益行为覆盖重复请求、并发和五角色数据隔离。
