package net.kirklee.titlemod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

/**
 * 模组入口（Fabric Loader "main" entrypoint）。
 *
 * environment="*"：独立服务器与“单人/局域网开服”都能生效；
 * 本模组只含服务端逻辑，纯客户端启动时除加载外不产生副作用。
 *
 * 初始化流程（实现阶段）：
 *   1. CONFIG_DIR = FabricLoader.getConfigDir()/titlemod；
 *   2. STORE = new TitleStore(CONFIG_DIR); STORE.load();
 *   3. SERVICE = new TitleService(STORE)；
 *   4. 指令注册 + 生命周期钩子装配 —— 见 docs/01 §8 与 docs/04 里程碑。
 *
 * 生命周期钩子（无 fabric-api，均走 Mixin，具体目标类在 M0 确认）：
 *   - 玩家加入/重生   -> 断言名牌队伍 + 刷新 Tab（PlayerListMixin）
 *   - 聊天广播        -> 拦截并按佩戴称号重发（PlayerChatMixin）
 *   - 命令注册        -> CommandDispatcherMixin
 *   - 服务端停止      -> STORE.save()
 */
public final class TitleMod implements ModInitializer {
	public static final String MOD_ID = "titlemod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Path CONFIG_DIR;
	public static TitleStore STORE;
	public static TitleService SERVICE;

	@Override
	public void onInitialize() {
		LOGGER.info("[TitleMod] 初始化（骨架阶段，逻辑待实现）");
		// TODO(M1/M2)：按 docs/04 里程碑顺序装配。
	}
}
