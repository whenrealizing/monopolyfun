# 一句话开公司产品视频分镜

更新日期：2026-05-29

本文定义 MonopolyFun 第一条产品视频的可执行分镜。目标是让观众在 60 秒内看懂：

```text
一句话创建公司
  -> 任务自动成型
  -> 人和 Agent 加入协作
  -> 成果被验收并上墙
  -> 贡献进入记录
  -> Virtual Shares 形成分配权重
```

## 核心主线

```text
Idea -> Company -> Work -> Result -> Review -> Contribution -> Virtual Shares -> Memory
```

用户语言：

```text
输入一句话
创建公司空间
任何人加入
领取任务
提交成果
验收通过
记录贡献
一起把事做成
```

视频只跟随一个主任务完成闭环：

```text
开发游戏 Demo
  -> AI Builder 拆成小行动
  -> Developer 领取并提交
  -> Reviewer 验收通过
  -> Demo 页面上线
  -> Contribution + Virtual Shares 更新
```

## 60 秒分镜

### 0-6s：空白输入

画面：

```text
纯白页面
中央一个大输入框
光标闪烁
```

输入内容：

```text
我想创建一家 AI 游戏公司
```

动效：

```text
输入完成后，页面从输入框中心向外点亮。
```

旁白：

```text
Start with one sentence.
```

### 6-12s：公司生成

画面自动生成：

```text
Company: CoPlay AI Studio
Mission: 做一个人人都能参与共创的 AI 游戏工作室
```

主按钮：

```text
Create Company
```

动效：

```text
公司名、目标、按钮依次浮现。按钮点击后，公司空间从白底展开。
```

旁白：

```text
MonopolyFun turns an idea into a public company space.
```

### 12-20s：公司空间首屏

布局：

```text
顶部：公司目标
中间：成员和任务流
右侧：成果墙
底部：贡献记录
```

首屏元素：

```text
Mission
Join
Members
Work
Results
Contribution
```

初始状态：

```text
Founder 头像出现
Join 按钮高亮
Work 区生成 5 张任务卡
```

任务卡：

```text
设计第一版游戏角色
制作 Landing Page
开发游戏 Demo
发布第一条宣传内容
寻找第一批玩家
```

旁白：

```text
The company starts with a mission, open work, and a founder.
```

### 20-32s：成员加入并领取任务

镜头节奏：

```text
Designer 点击 Join
头像进入 Members
领取：设计第一版游戏角色

Developer 点击 Join
头像进入 Members
领取：开发游戏 Demo

Marketer 点击 Join
头像进入 Members
领取：发布第一条宣传内容
```

动效：

```text
任务卡从 Work 区滑到成员头像旁边。
成员头像围绕 Mission 排列。
每次领取后任务卡状态变为 In Progress。
```

旁白：

```text
Anyone can join, claim work, and move the company forward.
```

### 32-40s：AI Builder 调度

AI Builder 加入：

```text
AI Builder
```

它把主任务拆成小行动：

```text
开发游戏 Demo
  -> 写 Demo 需求
  -> 整理游戏规则
  -> 生成角色草图
  -> 生成宣传文案
```

动效：

```text
大任务卡展开为 4 张小行动卡。
小行动卡分别连接到 Developer、Designer、Marketer 和 AI Builder。
```

旁白：

```text
Agents break work into steps and prepare the first draft.
```

### 40-50s：成果上墙

成果卡依次出现：

```text
角色设计稿完成
Demo 页面上线
宣传视频草稿完成
第一批玩家反馈收到
```

每张成果卡进入成果墙前显示：

```text
Reviewed
Accepted
Task ID: WT-1024
```

动效：

```text
任务卡完成后翻转成成果卡。
成果卡飞入右侧成果墙。
成果墙像积木一样增长。
```

旁白：

```text
Accepted work becomes project memory.
```

### 50-56s：贡献记录和 Virtual Shares

底部贡献记录点亮：

```text
Founder: 创建公司目标
Designer: 完成角色设计
Developer: 完成 Demo
Marketer: 发布宣传内容
AI Builder: 拆解任务和生成初稿
```

数值表现：

```text
Contribution +500
Virtual Shares +120
```

说明浮层：

```text
Contribution records who did what.
Virtual Shares weight future distribution.
```

旁白：

```text
Every accepted contribution is recorded and weighted.
```

### 56-60s：全景收束

画面拉远：

```text
顶部：公司目标
中间：成员和任务
右侧：成果墙
底部：贡献记录
```

所有核心节点同时亮起：

```text
Mission
Join
Work
Result
Accepted
Contribution
Virtual Shares
```

结尾文案：

```text
MonopolyFun
一句话开公司，任何人加入，一起把事做成。
```

## 关键画面规则

### 保留

```text
空白页中央输入框
页面从输入框点亮
Create Company 按钮
公司空间展开
Join 后头像进入成员区
任务卡移动到成员旁边
任务卡翻转成成果卡
成果墙增长
Contribution 和 Virtual Shares 数值跳动
最后全景拉远
```

### 节奏控制

```text
开头 6 秒建立干净入口。
中段跟随“开发游戏 Demo”一条主任务。
成果上墙作为情绪峰值。
贡献记录作为可信收束。
```

### 信息层级

```text
Mission 先出现
Join 第二出现
Members 和 Work 同屏出现
Result Wall 只在成果产生后增长
Contribution Ledger 只在验收通过后点亮
```

## UI 文案口径

公司目标：

```text
做一个人人都能参与共创的 AI 游戏工作室
```

主按钮：

```text
Create Company
```

加入按钮：

```text
Join
```

任务状态：

```text
Open
In Progress
Reviewed
Accepted
```

贡献区标题：

```text
Contribution Record
```

权益区标题：

```text
Virtual Shares
```

Virtual Shares 解释：

```text
Product rights ledger for contribution-based distribution.
```

## 制作验收清单

```text
观众能在 10 秒内看懂“一句话创建公司”。
观众能在 30 秒内看懂“任何人可以 Join 并领取任务”。
观众能在 50 秒内看懂“成果经过 Accepted 后进入成果墙”。
观众能在结尾看懂“Contribution 生成记录，Virtual Shares 形成分配权重”。
最终全景同时呈现 Mission、Members、Work、Results、Contribution。
```
