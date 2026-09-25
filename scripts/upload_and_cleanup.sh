#!/usr/bin/env bash
# =============================================================================
#  Omni Tool - GitHub upload + local cleanup
# =============================================================================
#  What this script does, in order:
#    1. sanity checks (git present, .env present, token filled in)
#    2. git repository bootstrap on the `main` branch
#    3. batched, meaningful commits (project skeleton -> registry -> item ->
#       dynamic recipes -> client -> data generation -> docs -> scripts)
#    4. push to GitHub over HTTPS using the token from .env
#    5. verification that the remote branch really points at our commit
#    6. ONLY THEN: print exactly what will be deleted and ask for confirmation,
#       then remove the build artifacts, caches and the token file
#
#  Every step is fail-fast (`set -euo pipefail`): if anything goes wrong the
#  script stops immediately and never reaches the cleanup phase.
#
#  Usage:
#      bash scripts/upload_and_cleanup.sh
#
#  Optional environment overrides:
#      CLEANUP_CONFIRM=yes        skip the interactive confirmation
#      ALLOW_EXISTING_REMOTE=yes  merge commits already present on the remote branch instead of
#                                 stopping (typical for the "Initial commit" GitHub creates)
#      EXTRA_CLEAN_TARGETS="..."  extra absolute paths to delete during cleanup
#      SKIP_CLEANUP=yes           upload only
#      GIT_AUTHOR_NAME / GIT_AUTHOR_EMAIL
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_DIR}/.env"

log()  { printf '\033[1;34m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"; }
ok()   { printf '\033[1;32m   ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m   ! %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m   ✘ %s\033[0m\n' "$*" >&2; exit 1; }

# -----------------------------------------------------------------------------
# 1. sanity checks
# -----------------------------------------------------------------------------
log "[1/7] 检查运行环境"
command -v git >/dev/null 2>&1 || die "未找到 git，请先安装 git"
[ -f "${ENV_FILE}" ] || die "缺少 ${ENV_FILE}（先执行 cp .env.example .env 并填入 token）"

# shellcheck disable=SC1090
set -a; . "${ENV_FILE}"; set +a

: "${GITHUB_USERNAME:?请在 .env 中设置 GITHUB_USERNAME}"
: "${GITHUB_REPO:?请在 .env 中设置 GITHUB_REPO}"
GITHUB_BRANCH="${GITHUB_BRANCH:-main}"

if [ -z "${GITHUB_TOKEN:-}" ]; then
  die "GITHUB_TOKEN 为空。请把 Personal Access Token 粘贴到 ${ENV_FILE} 的 GITHUB_TOKEN= 之后"
fi

# The token is never printed. Only this redacted URL appears in the logs.
PUBLIC_URL="https://github.com/${GITHUB_USERNAME}/${GITHUB_REPO}.git"
AUTHENTICATED_URL="https://${GITHUB_USERNAME}:${GITHUB_TOKEN}@github.com/${GITHUB_USERNAME}/${GITHUB_REPO}.git"

ok "git $(git --version | awk '{print $3}')"
ok "目标仓库 ${PUBLIC_URL} (分支 ${GITHUB_BRANCH})"
ok "项目目录 ${PROJECT_DIR}"

# -----------------------------------------------------------------------------
# 2. git bootstrap
# -----------------------------------------------------------------------------
log "[2/7] 初始化 git 仓库"
cd "${PROJECT_DIR}"

if [ ! -d .git ]; then
  git init -q -b "${GITHUB_BRANCH}" 2>/dev/null || { git init -q; git symbolic-ref HEAD "refs/heads/${GITHUB_BRANCH}"; }
  ok "已创建本地仓库"
else
  git symbolic-ref --quiet HEAD "refs/heads/${GITHUB_BRANCH}" || true
  ok "复用已存在的本地仓库"
fi

git config --local user.name  "${GIT_AUTHOR_NAME:-${GITHUB_USERNAME}}"
git config --local user.email "${GIT_AUTHOR_EMAIL:-${GITHUB_USERNAME}@users.noreply.github.com}"
git config --local commit.gpgsign false

# Safety net: the token file must be ignored by git and must never be staged.
if ! git check-ignore -q ".env"; then
  die ".env 未被 .gitignore 忽略，存在误提交 token 的风险。请检查 .gitignore 后重试"
fi
if git diff --cached --name-only | grep -qx "\.env"; then
  die ".env 已被放入暂存区。请执行 git reset -- .env 后重试"
fi
ok ".env 已被 git 忽略，不会进入提交"

# -----------------------------------------------------------------------------
# 3. batched commits
# -----------------------------------------------------------------------------
log "[3/7] 分批提交"

commit_group() {
  local message="$1"; shift
  local staged=0
  for path in "$@"; do
    if [ -e "${path}" ]; then
      git add -A -- "${path}"
      staged=1
    fi
  done
  if [ "${staged}" -eq 0 ]; then
    warn "跳过（路径不存在）：${message}"
    return
  fi
  if git diff --cached --quiet; then
    warn "跳过（无变更）：${message}"
    return
  fi
  git commit -q -m "${message}"
  ok "${message}"
}

commit_group "chore: 初始化 Forge 1.20.1 (47.x) 工程骨架" \
  build.gradle settings.gradle gradle.properties gradle gradlew gradlew.bat .gitignore LICENSE

commit_group "feat(registry): 材料模型与原版六 Tier 全能工具注册" \
  src/main/java/com/omnitool/omni_tool/registry/OmniToolMaterial.java \
  src/main/java/com/omnitool/omni_tool/registry/ModItems.java \
  src/main/resources/META-INF/mods.toml src/main/resources/pack.mcmeta

commit_group "feat(item): 全能工具物品逻辑（三 Tag 挖掘/剑式攻击/附魔/耐久）" \
  src/main/java/com/omnitool/omni_tool/item \
  src/main/java/com/omnitool/omni_tool/OmniToolMod.java

commit_group "feat(recipe): 运行时发现模组材料并动态克隆镐子配方" \
  src/main/java/com/omnitool/omni_tool/recipe \
  src/main/java/com/omnitool/omni_tool/registry/DynamicOmniToolRegistrar.java \
  src/main/java/com/omnitool/omni_tool/event

commit_group "feat(client): 动态注册物品的兜底模型映射" \
  src/main/java/com/omnitool/omni_tool/client

commit_group "feat(datagen): 配方/模型/中英语言文件/Tag 数据生成" \
  src/main/java/com/omnitool/omni_tool/datagen src/generated

commit_group "docs: README / CHANGELOG / HANDOFF / CONTRIBUTING" \
  README.md CHANGELOG.md HANDOFF.md CONTRIBUTING.md docs

commit_group "chore(scripts): 上传与清理脚本、密钥模板" \
  scripts .env.example

# Anything the groups above did not cover (future files) still gets committed.
if [ -n "$(git status --porcelain)" ]; then
  git add -A
  if ! git diff --cached --quiet; then
    git commit -q -m "chore: 其余工程文件"
    ok "chore: 其余工程文件"
  fi
fi

COMMIT_COUNT="$(git rev-list --count HEAD)"
LOCAL_SHA="$(git rev-parse HEAD)"
ok "本地共 ${COMMIT_COUNT} 个提交，HEAD=${LOCAL_SHA}"

# -----------------------------------------------------------------------------
# 4. push
# -----------------------------------------------------------------------------
log "[4/7] 推送到 GitHub"

if ! command -v curl >/dev/null 2>&1; then
  die "未找到 curl，无法校验远程仓库状态"
fi

EXISTING_HEADS="$(git ls-remote --heads "${PUBLIC_URL}" 2>/dev/null || true)"
REMOTE_HEAD_SHA="$(echo "${EXISTING_HEADS}" | awk -v b="refs/heads/${GITHUB_BRANCH}" '$2 == b {print $1}')"

if [ -n "${REMOTE_HEAD_SHA}" ] && ! git merge-base --is-ancestor "${REMOTE_HEAD_SHA}" HEAD 2>/dev/null; then
  # The remote branch holds commits we do not have. The usual cause is the "Initial commit"
  # GitHub creates together with a new repository (README/LICENSE/.gitignore).
  if [ "${ALLOW_EXISTING_REMOTE:-no}" != "yes" ]; then
    warn "远程 ${GITHUB_BRANCH} 存在本地没有的提交："
    echo "${EXISTING_HEADS}" | sed 's/^/     /'
    die "为安全起见已停止（不会覆盖远程历史）。确认要合并时请用 ALLOW_EXISTING_REMOTE=yes 重新执行"
  fi
  ok "ALLOW_EXISTING_REMOTE=yes：把远程已有提交合并进本地历史（文件冲突时以本工程为准）"
  git fetch -q "${AUTHENTICATED_URL}" "${GITHUB_BRANCH}"
  git merge --allow-unrelated-histories -X ours --no-edit \
    -m "chore: 合并远程 ${GITHUB_BRANCH} 的既有提交（冲突以本工程文件为准）" FETCH_HEAD
  ok "已合并远程历史"
fi

# Recompute after a possible merge: the push and the verification below must use the final HEAD.
COMMIT_COUNT="$(git rev-list --count HEAD)"
LOCAL_SHA="$(git rev-parse HEAD)"
ok "待推送：${COMMIT_COUNT} 个提交，HEAD=${LOCAL_SHA}"

git push "${AUTHENTICATED_URL}" "HEAD:refs/heads/${GITHUB_BRANCH}"
ok "推送完成"

# -----------------------------------------------------------------------------
# 5. verify the remote
# -----------------------------------------------------------------------------
log "[5/7] 校验远程仓库"
REMOTE_SHA="$(git ls-remote "${PUBLIC_URL}" "refs/heads/${GITHUB_BRANCH}" | awk '{print $1}')"
[ -n "${REMOTE_SHA}" ] || die "远程分支 ${GITHUB_BRANCH} 不存在，推送可能失败"
[ "${REMOTE_SHA}" = "${LOCAL_SHA}" ] || die "远程 SHA(${REMOTE_SHA}) 与本地 SHA(${LOCAL_SHA}) 不一致"
ok "远程 ${GITHUB_BRANCH} = ${REMOTE_SHA}（与本地一致）"

# -----------------------------------------------------------------------------
# 6. cleanup (only after a verified push)
# -----------------------------------------------------------------------------
log "[6/7] 本地清理"

if [ "${SKIP_CLEANUP:-no}" = "yes" ]; then
  warn "SKIP_CLEANUP=yes，跳过清理"
else
  CLEAN_TARGETS=()
  add_target() { [ -e "$1" ] && CLEAN_TARGETS+=("$1"); }

  add_target "${ENV_FILE}"                                   # 含 token，必须删
  add_target "${PROJECT_DIR}/build"                          # Gradle 构建产物
  add_target "${PROJECT_DIR}/.gradle"                        # Gradle 项目缓存
  add_target "${PROJECT_DIR}/run"                            # 运行目录
  add_target "${PROJECT_DIR}/run-data"                       # runData 工作目录
  add_target "${PROJECT_DIR}/logs"                           # 运行日志
  add_target "${HOME}/.gradle"                               # Gradle 依赖 / wrapper 缓存
  add_target "/tmp/omni-ref"                                 # 参考用下载（client.jar / 映射 / 反编译器）
  add_target "/tmp/omni-setup.log"
  add_target "/tmp/tasks-list.log"
  add_target "/tmp/cp.gradle"
  # 放在最后：脚本自身就在这个目录里。Linux 下删除已打开的文件不影响继续执行，
  # 但把它排在最后可以避免任何依赖该目录的后续操作。
  add_target "/tmp/omni-tool"                                # 构建副本（如有）

  # 通过 EXTRA_CLEAN_TARGETS 传入额外的绝对路径（以空格分隔；路径中不要含空格）。
  # 例如把另一个位置的工程副本一并清掉：
  #   EXTRA_CLEAN_TARGETS="/path/to/copy" bash scripts/upload_and_cleanup.sh
  if [ -n "${EXTRA_CLEAN_TARGETS:-}" ]; then
    # shellcheck disable=SC2086
    for extra in ${EXTRA_CLEAN_TARGETS}; do
      add_target "${extra}"
    done
  fi

  printf '\n\033[1;33m'
  printf '┌────────────────────────────── 清理确认 ──────────────────────────────┐\n'
  printf '│ 远程仓库已确认收到全部提交，接下来将删除以下本地内容：              │\n'
  printf '└─────────────────────────────────────────────────────────────────────┘\n'
  printf '\033[0m'
  for target in "${CLEAN_TARGETS[@]}"; do
    printf '   - %s\n' "${target}"
  done
  printf '\n'

  if [ "${CLEANUP_CONFIRM:-}" != "yes" ]; then
    printf '确认清理以上内容？输入 yes 继续（其它任何输入都会取消）：'
    read -r answer
    [ "${answer}" = "yes" ] || { warn "已取消清理，本地文件保持不变"; exit 0; }
  else
    ok "CLEANUP_CONFIRM=yes，跳过交互确认"
  fi

  for target in "${CLEAN_TARGETS[@]}"; do
    rm -rf -- "${target}"
    ok "已删除 ${target}"
  done
fi

# -----------------------------------------------------------------------------
# 7. report
# -----------------------------------------------------------------------------
log "[7/7] 完成"
CLEANUP_REPORT="构建产物 / Gradle 缓存 / run 目录 / .env / 临时文件"
if [ "${SKIP_CLEANUP:-no}" = "yes" ]; then
  CLEANUP_REPORT="（已跳过）"
fi
cat <<EOF

  仓库地址 : ${PUBLIC_URL}
  分支     : ${GITHUB_BRANCH}
  提交数量 : ${COMMIT_COUNT}
  HEAD     : ${LOCAL_SHA}
  清理范围 : ${CLEANUP_REPORT}

EOF
