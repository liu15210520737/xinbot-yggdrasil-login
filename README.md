# YggdrasilLogin

[Xinbot](https://github.com/huangdihd/xinbot) 第三方插件：通过 Yggdrasil 兼容的外置登录认证服务器（如 [LittleSkin](https://littleskin.cn)）登录，让机器人能进入开启了外置登录（authlib-injector）的正版验证服务器。

**无需修改 Xinbot 本体** —— 插件在每次连接前，通过 mcprotocollib 的 session flag 注入外置登录所需的档案、令牌和会话服务。

## 原理

Xinbot 在建立连接前会触发 `ConnectEvent`，此时底层 mcprotocollib session 已经创建但尚未连接。插件在此时完成三件事：

1. 向 `{authServer}/authserver/authenticate` 登录（或复用/刷新本地保存的令牌）；
2. 把 `GameProfile`（UUID + 角色名）和 access token 写入 session flag；
3. 把一个指向外置 sessionserver 的 `SessionService` 写入 session flag，替代 mcprotocollib 内置的 Mojang 会话服务。

服务器发起加密请求时，mcprotocollib 会读取这些 flag，用外置服务器的地址完成 joinServer 验证。重连时同样生效。

## 安装

1. 从 [Releases](../../releases) 下载 `YggdrasilLogin-x.x.x-RELEASE.jar`，放入 Xinbot 的插件目录（默认 `./plugin/`）。
2. 启动一次 Xinbot，插件会自动生成 `yggdrasil.conf` 并提示未配置。
3. 编辑 `yggdrasil.conf`：

```hocon
authServer = "https://littleskin.cn/api/yggdrasil"   # 外置登录服务器地址
username = "your@email.com"                          # 外置登录账号
password = "yourpassword"                            # 外置登录密码
```

4. 重启 Xinbot 或在控制台重载插件。`config.conf` 中保持 `onlineMode: false` 即可，插件会接管认证。

登录成功后，令牌保存在 `yggdrasil-session.json`，之后重连会直接复用或刷新令牌，不会重复请求密码登录。

## 构建

```bash
mvn package
```

依赖 `com.github.huangdihd:xinbot:2.4.3-RELEASE`（JitPack，provided 作用域，运行时由 Xinbot 本体提供）。

## 许可证

GPL-3.0
