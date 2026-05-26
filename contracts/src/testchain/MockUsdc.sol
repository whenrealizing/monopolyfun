// SPDX-License-Identifier: MIT
pragma solidity ^0.8.30;

contract MockUsdc {
    string private constant TOKEN_NAME = "Mock USDC";
    string private constant TOKEN_SYMBOL = "mUSDC";
    uint8 private constant TOKEN_DECIMALS = 6;

    mapping(address account => uint256 balance) public balanceOf;
    mapping(address owner => mapping(address spender => uint256 amount)) public allowance;

    event Transfer(address indexed from, address indexed to, uint256 amount);
    event Approval(address indexed owner, address indexed spender, uint256 amount);

    function name() external pure returns (string memory) {
        return TOKEN_NAME;
    }

    function symbol() external pure returns (string memory) {
        return TOKEN_SYMBOL;
    }

    function decimals() external pure returns (uint8) {
        return TOKEN_DECIMALS;
    }

    function mint(address to, uint256 amount) external {
        // 测试链直接铸币，保证收益入账链路可重复验证。
        require(to != address(0), "MockUsdc: zero recipient");
        balanceOf[to] += amount;
        emit Transfer(address(0), to, amount);
    }

    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        emit Approval(msg.sender, spender, amount);
        return true;
    }

    function transfer(address to, uint256 amount) external returns (bool) {
        _transfer(msg.sender, to, amount);
        return true;
    }

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        // 保持 ERC20 授权语义，模拟真实付款账户向分账合约入金。
        uint256 allowed = allowance[from][msg.sender];
        require(allowed >= amount, "MockUsdc: allowance");
        if (allowed != type(uint256).max) {
            allowance[from][msg.sender] = allowed - amount;
        }
        _transfer(from, to, amount);
        return true;
    }

    function _transfer(address from, address to, uint256 amount) private {
        require(to != address(0), "MockUsdc: zero recipient");
        require(balanceOf[from] >= amount, "MockUsdc: balance");
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        emit Transfer(from, to, amount);
    }
}
