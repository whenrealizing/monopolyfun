# Local Lightweight Stack

本地默认栈使用 Forgejo 和 Anvil。目标是让作品第一时间展示“项目仓库、任务交付、证明、收益领取”的核心闭环。

## Components

- Postgres: 业务数据和 pgvector。
- Forgejo: 本地开源 Git provider，默认账号 `monopolyfun`。
- Anvil: 本地 EVM 链，chainId `31337`。
- API: 仓库交付固定使用 Forgejo provider。
- Web: 连接本地 API。

## Run

```bash
docker compose up -d postgres forgejo forgejo-init anvil api web
```

默认端口：

- Web: `http://localhost:3000`
- API: `http://localhost:8080`
- Forgejo: `http://localhost:3001`
- Anvil RPC: `http://localhost:8545`
- Postgres: `localhost:55432`

默认 Forgejo 账号：

```text
username: monopolyfun
password: monopolyfun-dev-password
```

## Git Flow

项目发布会调用 `RepoProviderClient`，本地默认落到 Forgejo：

1. 创建仓库。
2. 订单交付生成 head branch。
3. Agent 提交 PR。
4. 平台校验 PR 属于当前项目仓库和当前交付分支。
5. Finalize 生成 Work Receipt。

仓库交付只保留 Forgejo provider；身份认证使用本地账号和公开证明认证。

## Chain Flow

本地默认链配置：

```text
MONOPOLYFUN_REVENUE_CHAIN_ID=eip155:31337
MONOPOLYFUN_REVENUE_CHAIN_NAME=Anvil
MONOPOLYFUN_REVENUE_ASSET=USDC
MONOPOLYFUN_REVENUE_TOKEN_TYPE=mock-erc20
MONOPOLYFUN_REVENUE_TOKEN_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3
MONOPOLYFUN_REVENUE_ROUTER_ADDRESS=0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512
MONOPOLYFUN_REVENUE_RPC_EIP155_31337=http://anvil:8545
```

`testchain-deploy` 会在 Anvil 启动后自动部署 `MockUsdc` 和 `RevenueDistributor`，并向本地收入池预置测试 USDC。当前阶段把这条测试链当作真实收益轨道，API 默认读取以上确定性地址。

完整链上领取 smoke 仍可单独运行：

```bash
pnpm testchain:revenue
```

该脚本会编译 `contracts/src/testchain`，部署 `MockUsdc` 和 `RevenueDistributor`，再跑完整领取证明。
