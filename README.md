# Escrow 托管交易后端

## 1. 如何运行

运行环境：Java 21，使用项目自带的 Gradle Wrapper。

在项目根目录配置后端账户私钥：

```bash
read -rsp "请输入 BE 测试账户私钥：" WEB3J_PRIVATE_KEY
export WEB3J_PRIVATE_KEY
```

启动：

```bash
bash gradlew bootRun
```

如果需要通过本实验的宿主机代理访问 RPC，使用：

```bash
JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=10.0.2.2 -Dhttp.proxyPort=10808 -Dhttps.proxyHost=10.0.2.2 -Dhttps.proxyPort=10808 -Dhttp.nonProxyHosts=localhost|127.*" \
bash gradlew --no-daemon bootRun
```

代理地址和端口按实际网络环境修改。

## 2. 环境变量

| 变量 | 是否必需 | 说明 |
|---|---|---|
| WEB3J_PRIVATE_KEY | 是 | 后端 BE 测试账户私钥，由环境变量注入，不写入仓库 |
| JAVA_TOOL_OPTIONS | 按需 | Java 代理参数，网络直连正常时不需要设置 |

在启动服务的同一个终端中设置变量。

`application.properties` 中使用：

```properties
web3j.private-key=${WEB3J_PRIVATE_KEY}
```

## 3. 合约地址

网络：POTOS 测试网。

| 项目 | 地址或数值 |
|---|---|
| 路线 A Escrow | 0xf576c92862c43deb9ba1bf272d46f33282f5677c2fc32a67bac8d68c13994024 |
| QZSToken | 0x459052b6295A407aA9FD049A2Da8e71640e6B5B8 |
| BE／仲裁人 | 0xc411ca291e762734227606B389acE353FcA1FfBe |

## 4. 交易记录

| 操作 | 交易哈希 | 区块浏览器链接 | 发起账户 |
|---|---|---|---|
| 部署 Escrow | 0xf576c92862c43deb9ba1bf272d46f33282f5677c2fc32a67bac8d68c13994024 | POTOS Testnet transaction 0xf576c92862c43deb9ba1bf272d46f33282f5677c2fc32a67bac8d68c13994024 | 0xc411ca291e762734227606B389acE353FcA1FfBe |
| createOrder 订单 0 | 0xd5895b5c5f59563c6e5aa1f84df852f66b1b5775b7316e1f56cea9d2fbc7b667 | POTOS Testnet transaction 0xd5895b5c5f59563c6e5aa1f84df852f66b1b5775b7316e1f56cea9d2fbc7b667 | 0x3b5e473e2c82125A15C47D9c25DB02f86690EaA2 |
| approve | 0x46cb7d47830c10d31d5b539bd1647c35fd4b5de59da5e96e8be616f5156b9d10 | POTOS Testnet transaction 0x46cb7d47830c10d31d5b539bd1647c35fd4b5de59da5e96e8be616f5156b9d10 | 0x3b5e473e2c82125A15C47D9c25DB02f86690EaA2 |
| deposit 订单 0 | 0x66a06016915cdb82b369d530d9e30120972c6373ef4c0ffecf72c26ea7a3f654 | POTOS Testnet transaction 0x66a06016915cdb82b369d530d9e30120972c6373ef4c0ffecf72c26ea7a3f654 | 0x3b5e473e2c82125A15C47D9c25DB02f86690EaA2 |
| dispute 订单 1 | 0x0b512ed37610867e4691800e7465a017ef61226afd8900db20094180ce30e6eb | POTOS Testnet transaction 0x0b512ed37610867e4691800e7465a017ef61226afd8900db20094180ce30e6eb | 0x3B3e7978970Cc4D167511DEf28Da4BA20855678d |
| resolve 订单 1 | 0x0ed461d32b83ab00cb120e442dbd8bc9148b10167ca5ea3bcdd5d44e576c3b82 | POTOS Testnet transaction 0x0ed461d32b83ab00cb120e442dbd8bc9148b10167ca5ea3bcdd5d44e576c3b82 | 0xc411ca291e762734227606B389acE353FcA1FfBe |
| confirm 订单 0 | 0xa1078bfbf761b3f7de5f6813bba20102f3dd5a415974d948b2053611ff716374 | POTOS Testnet transaction 0xa1078bfbf761b3f7de5f6813bba20102f3dd5a415974d948b2053611ff716374 | 0x3b5e473e2c82125A15C47D9c25DB02f86690EaA2 |
| resolve 失败（路线 A） | 0xadd0922bd38ee930409df47847a6dc0a7fe78af37d5c76def19e8998a06e2ea9 | POTOS Testnet transaction 0xadd0922bd38ee930409df47847a6dc0a7fe78af37d5c76def19e8998a06e2ea9 | 0xc411ca291e762734227606B389acE353FcA1FfBe |
