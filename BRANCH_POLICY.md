# Crypto SDK - 分支管理规范

## 分支总览

```
master              ← 生产稳定版本（受保护）
  └── release/1.1.x ← 版本发布分支（受保护）
        └── fix/*   ← 缺陷修复分支（审核合并到 release）

dev                 ← 开发主线（受保护，SNAPSHOT 版本）
  ├── feature/*     ← 新功能开发分支
  └── fix/*         ← 缺陷修复分支
```

---

## 分支说明

### 1. `master` — 主分支（受保护）

| 属性 | 规则 |
|------|------|
| **用途** | 生产环境稳定版本，每个提交对应一个正式发布版本 |
| **版本号** | 正式版本号，如 `1.1.1`、`1.2.0` |
| **推送权限** | **禁止所有人直接推送**（包括仓库 owner） |
| **合并来源** | 仅接受 `release/x.x.x` 分支的 Pull Request 合并 |
| **Tag** | 每次合并后打 Tag，如 `v1.1.1` |

### 2. `dev` — 开发分支（受保护）

| 属性 | 规则 |
|------|------|
| **用途** | 开发主线，包含最新已验证的功能代码 |
| **版本号** | SNAPSHOT 版本号，如 `1.1.2-SNAPSHOT`、`1.2.0-SNAPSHOT` |
| **推送权限** | **禁止所有人直接推送**（包括仓库 owner） |
| **合并来源** | 仅接受 `feature/*`、`fix/*` 分支的 Pull Request 合并 |
| **下游** | 功能稳定后合并到 `release/x.x.x`，最终进入 `master` |

### 3. `release/x.x.x` — 版本发布分支（受保护）

| 属性 | 规则 |
|------|------|
| **用途** | 特定大版本的发布维护分支，留存正式版本的代码快照 |
| **命名规则** | `release/` + 大版本号，如 `release/1.1.x`、`release/1.2.x` |
| **版本号** | 正式版本号，如 `1.1.1` |
| **推送权限** | **禁止直接推送** |
| **合并来源** | 仅接受 `fix/*` 分支的 Pull Request（需 Code Review 审核） |
| **下游** | 验证通过后合并到 `master` |

> **说明**：`release/1.1.x` 中的 `x` 表示该大版本下的迭代补丁号。每当有新的 fix 合并并验证通过后，`x` 会递增（如 1.1.1 → 1.1.2），同时更新分支上的版本号。

### 4. `fix/*` — 缺陷修复分支

| 属性 | 规则 |
|------|------|
| **用途** | 修复已发布版本的缺陷 |
| **命名规则** | `fix/` + 简要描述，如 `fix/replay-attack-bypass`、`fix/aes-key-length` |
| **基于分支** | 从 `release/x.x.x` 创建（修复已发布版本）或从 `dev` 创建（修复开发中问题） |
| **合并目标** | → `release/x.x.x`（需审核）或 → `dev` |

### 5. `feature/*` — 新功能开发分支

| 属性 | 规则 |
|------|------|
| **用途** | 新功能开发期间的隔离分支 |
| **命名规则** | `feature/` + 简要描述，如 `feature/sm4-support`、`feature/async-encrypt` |
| **基于分支** | 从 `dev` 创建 |
| **合并目标** | → `dev`（功能完成后提交 PR 合并） |

### 6. 其他临时分支

| 类型 | 命名规则 | 用途 |
|------|----------|------|
| 实验性分支 | `experiment/*` | 技术验证、方案对比，验证后删除 |
| 热修复分支 | `hotfix/*` | 紧急生产修复，流程同 `fix/*` 但优先级更高 |

---

## 版本号规则

遵循 [语义化版本](https://semver.org/lang/zh-CN/) 规范：`MAJOR.MINOR.PATCH`

| 段位 | 含义 | 触发条件 |
|------|------|----------|
| MAJOR（主版本） | 不兼容的 API 变更 | 架构重构、加密算法替换等 |
| MINOR（次版本） | 向下兼容的功能新增 | 新增加密模式、新注解等 |
| PATCH（修订号） | 向下兼容的缺陷修复 | Bug 修复、安全补丁等 |

### 版本号与分支的对应关系

```
master 上的版本: 1.1.1 (正式发布)
  ↓
release/1.1.x:   1.1.1 → 1.1.2 → 1.1.3 ... (同一大版本的补丁迭代)
  ↓
dev 上的版本:     1.1.2-SNAPSHOT → 1.2.0-SNAPSHOT (开发中)
```

---

## 典型工作流

### 新功能开发

```
dev → feature/xxx → 开发完成 → PR 合并到 dev → 验证通过
  → 合并到 release/x.x.x → 测试验证 → PR 合并到 master → 打 Tag 发布
```

### 缺陷修复（已发布版本）

```
release/1.1.x → fix/xxx → 修复完成 → PR 审核合并到 release/1.1.x
  → 版本号更新(1.1.1→1.1.2) → PR 合并到 master → 打 Tag 发布
```

### 紧急热修复

```
master → hotfix/xxx → 修复完成 → PR 合并到 master → 打 Tag
  → 同步合并到 release/x.x.x 和 dev
```

---

## Gitee 分支保护配置

以下配置需在 Gitee Web UI 中手动设置：

### master 分支保护

路径：`管理` → `仓库设置` → `分支与标签保护` → `添加保护分支`

- 保护分支：`master`
- ✅ 禁止强制推送
- ✅ 开启合并请求审核（可选拥有写入权限的用户）
- ✅ 合并前要求通过 CI 检查（如有）

### dev 分支保护

- 保护分支：`dev`
- ✅ 禁止强制推送
- ✅ 开启合并请求审核

### release/* 分支保护

- 保护分支：`release/*`
- ✅ 禁止强制推送
- ✅ 开启合并请求审核

---

## Git 操作速查

```bash
# 从 dev 创建新功能分支
git checkout dev
git pull origin dev
git checkout -b feature/your-feature

# 功能完成后推送到远程，然后通过 Gitee Web UI 提交 PR 到 dev
git push origin feature/your-feature

# 从 release 创建修复分支
git checkout release/1.1.x
git pull origin release/1.1.x
git checkout -b fix/your-fix

# 修复完成后推送到远程，然后通过 Gitee Web UI 提交 PR 到 release/1.1.x
git push origin fix/your-fix

# release 验证通过后合并到 master（通过 Gitee Web UI 提交 PR）

# 打 Tag
git checkout master
git pull origin master
git tag v1.1.1
git push origin v1.1.1
```

---

**最后更新**: 2026-05-11
