# 贡献指南

感谢你的兴趣！动工前请花两分钟看完。

## 提交前

1. **先开 Issue 再动大改**：新功能或架构调整，请先说明动机与方案。
2. **从 `main` 拉分支**：`git checkout -b feat/your-feature`。
3. **本地门槛必须全绿**：

   ```bash
   cd fitness-backend && mvn -B clean package
   cd fitness-frontend && npm test && npm run build
   ```

4. **迁移文件只增不改**：新增变更请追加 `Vxx__xxx.sql`，不要修改已应用的历史迁移（Flyway 校验字节）。

## 提交信息

采用 Conventional Commits：

```
feat(booking): 支持候补自动转正
fix(finance): 修复退款并发重复入账
docs: 补充本地启动说明
```

## Pull Request

- 一个 PR 只做一件事；
- 说明 **改了什么 / 为什么 / 怎么验证**；
- 涉及资金、权限、数据迁移的改动，请附测试用例。

## 请勿提交

- 真实口令、API Key、`.env` 真值；
- 生产数据导出、真实会员信息、含个人信息的截图；
- `target/`、`dist/`、`node_modules/` 等构建产物。
