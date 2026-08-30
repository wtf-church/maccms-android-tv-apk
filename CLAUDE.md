# maccms-client-tv

## 远程测试服务器（120.25.76.65）

- 测试服务器：`root@120.25.76.65`（阿里云，maccms 站点）
- SSH 使用密钥 `~/.ssh/pipo_test` 连接：`ssh -i ~/.ssh/pipo_test root@120.25.76.65`
- 不要反复询问连接方式，直接使用该 key 连接。
- 服务器项目路径：`/www/wwwroot/maccms-client-tv`（git 仓库，remote 为 gitee）

## 远程测试同步流程（每次都照此执行）

1. 本地修改后 **commit + push** 到 gitee（`git@gitee.com:shuizhuyu/maccms_tv_client.git`）
2. 测试服务器 **pull**：`ssh -i ~/.ssh/pipo_test root@120.25.76.65 "cd /www/wwwroot/maccms-client-tv && git pull"`
3. 在服务器上 **构建 + 重启** 对应容器（如：`cd /www/wwwroot/maccms-client-tv/docker && docker compose up -d --build maccms`）
4. 验证服务可用

**Why:** 服务器上的代码必须与 git 仓库一致，直接 scp/手工改文件会导致后续 pull 冲突。

**How to apply:** 涉及远程测试服务器的任何改动，都走 commit→push→pull→build→restart 流程，不要用 scp 或直接在服务器上改文件。
