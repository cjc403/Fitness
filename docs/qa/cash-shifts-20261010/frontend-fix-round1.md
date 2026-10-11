# 前端独立复审修复：Round 1

复审指出 `reloadConflict` 的外层异步后续处理缺少动作代次验证：A交班发生2600，重新载入详情挂起；取消A并打开B，B再次发生2600；A旧详情请求被内部正确丢弃，但外层看到B非空详情便将B冲突锁清除，允许再次提交。

先在真实compiler-sfc编译的生产页面setup中复现，再修改。新增两个运行测试分别覆盖详情重载与开班工作台重载，每项均执行旧请求成功及失败返回两个分支：旧重载返回后，新动作仍然保持conflict=true，保留新草稿与目标，不增加第3次POST，不产生消息。两项修复前均断言失败（false不等于true）。

最小修复只改 `CashShiftCenter.vue` 的 `reloadConflict`：发起时捕获actionSequence、scopeSequence、动作类型和目标ID；详情分支另外捕获本次detailSequence，开班分支捕获listSequence；await后仅当同一有效动作、弹层仍打开、范围/目标/请求代次完全一致且本次读取成功时解除conflict。旧详情的内部丢弃逻辑保留。

修改文件：

- `fitness-frontend/src/views/admin/CashShiftCenter.vue`
- `fitness-frontend/tests/cash-shifts-runtime.test.mjs`
- `docs/qa/cash-shifts-20261010/frontend-build.json`（更新当前制品哈希）
- 本修复报告。原 `frontend-report.md` 保持历史证据不改。

最终验证时间：2026-10-10T16:13:59.2928534+08:00。

- 聚焦运行3项通过（2项新增竞态测试及现有正常2600重载测试），退出0。
- `node --test tests/*.test.mjs`：**131项，131通过，0失败，0跳过**，退出0。
- `npm run build`：退出0。仅保留既有大chunk提醒。
- `frontend-build.json` 更新5个产品源文件SHA256和69个当前dist文件哈希，含index.html。

私有原始证据位于 `C:\Users\CJC\AppData\Local\Temp\fitchain-cash-frontend-20261010`：`red-fix-round1.log`、`green-fix-round1.log`、`all-tests-fix-round1.log`、`build-fix-round1.log`、`verification-fix-round1.json`。未输出真实账号令牌或凭据。

没有启动服务、写真实数据、改动其他产品文件、提交、推送或部署。待主任务对修复范围独立复审，并接续桌面验收。

## 桌面门店选择器外观修复

主任务实际桌面截图及尺寸检查发现：WorkspaceHero actions的直接div布局规则作用到el-select根，虽然控件声明180px，内部wrapper被压至44px，导致门店文字不可见。

按主任务授权，仅在 `CashShiftCenter.vue` 的actions slot内给门店、日期和按钮统一增加 `<div class="cash-filters">` 外层容器。欢迎区现有flex规则作用到这层行容器，el-select不再成为直接div；保留180px选择器宽度。不改业务逻辑，不增加镜像CSS测试，实际桌面宽度与截图复验由主任务执行。

修后全量131/131通过（0失败/跳过），`npm run build`退出0；最终验证时间2026-10-10T16:16:23.1137928+08:00。更新 `frontend-build.json` 当前5个产品源文件及69个dist文件SHA256。独立F1复审继续按上节业务修复证据进行。

私有证据：同一临时目录下 `all-tests-hero-filter-fix.log`、`build-hero-filter-fix.log`、`verification-hero-filter-fix.json`。原始报告及前轮日志保留。
