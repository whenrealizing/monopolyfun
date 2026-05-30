#!/usr/bin/env bash
set -euo pipefail

RPC_URL="${MONOPOLYFUN_REVENUE_RPC_EIP155_31337:-http://anvil:8545}"
DEPLOYER_PRIVATE_KEY="${TESTCHAIN_DEPLOYER_PRIVATE_KEY:-0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80}"
PAYER_PRIVATE_KEY="${TESTCHAIN_PAYER_PRIVATE_KEY:-0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d}"
PAYER_ADDRESS="${TESTCHAIN_PAYER_ADDRESS:-0x70997970C51812dc3A010C7d01b50e0d17dc79C8}"
TOKEN_ADDRESS="${MONOPOLYFUN_REVENUE_TOKEN_ADDRESS:-0x5FbDB2315678afecb367f032d93F642f64180aa3}"
ROUTER_ADDRESS="${MONOPOLYFUN_REVENUE_ROUTER_ADDRESS:-0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512}"
SEED_AMOUNT="${TESTCHAIN_REVENUE_SEED_MINOR:-100000000000}"

wait_for_anvil() {
  for _ in $(seq 1 60); do
    if cast chain-id --rpc-url "$RPC_URL" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Anvil RPC is not ready: $RPC_URL" >&2
  return 1
}

deploy_if_needed() {
  local code
  code="$(cast code "$ROUTER_ADDRESS" --rpc-url "$RPC_URL")"
  if [ "$code" != "0x" ]; then
    echo "local revenue contracts already deployed at $ROUTER_ADDRESS"
    return 0
  fi

  # 中文注释：固定使用 Anvil 默认账户前两个 nonce 部署，API 默认地址即可代表本地真实收入轨道。
  forge build --contracts contracts/src/testchain >/dev/null
  forge create \
    --broadcast \
    --rpc-url "$RPC_URL" \
    --private-key "$DEPLOYER_PRIVATE_KEY" \
    contracts/src/testchain/MockUsdc.sol:MockUsdc >/tmp/mock-usdc-create.log
  forge create \
    --broadcast \
    --rpc-url "$RPC_URL" \
    --private-key "$DEPLOYER_PRIVATE_KEY" \
    contracts/src/testchain/RevenueDistributor.sol:RevenueDistributor \
    --constructor-args "$TOKEN_ADDRESS" >/tmp/revenue-distributor-create.log

  test "$(cast code "$TOKEN_ADDRESS" --rpc-url "$RPC_URL")" != "0x"
  test "$(cast code "$ROUTER_ADDRESS" --rpc-url "$RPC_URL")" != "0x"
}

seed_revenue_pool() {
  # 中文注释：为演示链预置测试 USDC 和收入池，领取流程可以直接作为真实闭环演示。
  cast send "$TOKEN_ADDRESS" \
    "mint(address,uint256)" "$PAYER_ADDRESS" "$SEED_AMOUNT" \
    --rpc-url "$RPC_URL" \
    --private-key "$DEPLOYER_PRIVATE_KEY" >/dev/null
  cast send "$TOKEN_ADDRESS" \
    "approve(address,uint256)" "$ROUTER_ADDRESS" "$SEED_AMOUNT" \
    --rpc-url "$RPC_URL" \
    --private-key "$PAYER_PRIVATE_KEY" >/dev/null
  cast send "$ROUTER_ADDRESS" \
    "pay(uint256)" "$SEED_AMOUNT" \
    --rpc-url "$RPC_URL" \
    --private-key "$PAYER_PRIVATE_KEY" >/dev/null
}

wait_for_anvil
deploy_if_needed
seed_revenue_pool

echo "local revenue track ready"
echo "token=$TOKEN_ADDRESS"
echo "router=$ROUTER_ADDRESS"
