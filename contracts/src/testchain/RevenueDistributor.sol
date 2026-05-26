// SPDX-License-Identifier: MIT
pragma solidity ^0.8.30;

interface IERC20Like {
    function transfer(address to, uint256 amount) external returns (bool);
    function transferFrom(address from, address to, uint256 amount) external returns (bool);
}

contract RevenueDistributor {
    address public immutable OWNER;
    IERC20Like public immutable TOKEN;

    mapping(bytes32 periodKey => bytes32 root) public distributionRoots;
    mapping(bytes32 claimKey => bool claimed) public claimed;

    event RevenuePaid(address indexed payer, uint256 amount);
    event DistributionRootSet(string period, bytes32 root, uint256 totalAmount);
    event Claimed(string period, address indexed account, uint256 amount);

    modifier onlyOwner() {
        _onlyOwner();
        _;
    }

    constructor(address tokenAddress) {
        require(tokenAddress != address(0), "RevenueDistributor: token required");
        OWNER = msg.sender;
        TOKEN = IERC20Like(tokenAddress);
    }

    function pay(uint256 amount) external {
        // 付款方先把测试币打入合约，后续 claim 只能从这笔收入池中转出。
        require(amount > 0, "RevenueDistributor: amount required");
        require(TOKEN.transferFrom(msg.sender, address(this), amount), "RevenueDistributor: pay failed");
        emit RevenuePaid(msg.sender, amount);
    }

    function setDistributionRoot(string calldata period, bytes32 root, uint256 totalAmount) external onlyOwner {
        // 后端完成分配计算后发布根，链上只验证根和领取证明。
        require(bytes(period).length > 0, "RevenueDistributor: period required");
        require(root != bytes32(0), "RevenueDistributor: root required");
        require(totalAmount > 0, "RevenueDistributor: total required");
        distributionRoots[periodKey(period)] = root;
        emit DistributionRootSet(period, root, totalAmount);
    }

    function claim(string calldata period, address account, uint256 amount, bytes32[] calldata proof) external {
        // 钱包领取时校验叶子和重复领取状态，确保同一收益份额只能提现一次。
        require(account != address(0), "RevenueDistributor: account required");
        require(amount > 0, "RevenueDistributor: amount required");
        bytes32 key = claimKey(period, account);
        require(!claimed[key], "RevenueDistributor: already claimed");
        bytes32 root = distributionRoots[periodKey(period)];
        require(root != bytes32(0), "RevenueDistributor: missing root");
        require(verify(root, leaf(period, account, amount), proof), "RevenueDistributor: invalid proof");

        claimed[key] = true;
        require(TOKEN.transfer(account, amount), "RevenueDistributor: transfer failed");
        emit Claimed(period, account, amount);
    }

    function periodKey(string memory period) public pure returns (bytes32) {
        return keccak256(bytes(period));
    }

    function claimKey(string memory period, address account) public pure returns (bytes32) {
        return keccak256(abi.encodePacked(period, account));
    }

    function leaf(string memory period, address account, uint256 amount) public pure returns (bytes32) {
        return keccak256(abi.encodePacked(period, account, amount));
    }

    function verify(bytes32 root, bytes32 value, bytes32[] calldata proof) public pure returns (bool) {
        // 使用排序 Merkle proof，前端和后端生成证明时无需关心左右位置。
        bytes32 computed = value;
        for (uint256 index = 0; index < proof.length; index++) {
            bytes32 sibling = proof[index];
            computed = computed <= sibling
                ? keccak256(abi.encodePacked(computed, sibling))
                : keccak256(abi.encodePacked(sibling, computed));
        }
        return computed == root;
    }

    function _onlyOwner() internal view {
        require(msg.sender == OWNER, "RevenueDistributor: owner required");
    }
}
