package net.kirklee.pdctitle;

import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDCTitle 入口（Fabric Loader "main" entrypoint）。
 *
 * environment="*"：独立服务器与“单人/局域网开服”都生效；纯服务端逻辑，客户端启动无副作用。
 *
 * 初始化顺序：
 *   CONFIG_DIR <- config/pdctitle
 *   CONFIG     <- config.json（chat/tab/nametag 总开关）
 *   STORE      <- definitions.json + players.json
 *   SERVICE    <- 编排层
 * 其余（聊天拦截/Tab/指令注册/玩家加入）由 Mixin 调用本类静态单例。
 *
 * 聊天拦截说明：签名与未签名（离线/第三方验证服）的玩家消息最终都会进入
 * PlayerList 的 4 参 broadcastChatMessage 总漏斗，由 PlayerListChatMixin 在那里
 * 拦截并以服务端系统行重发（称号块带悬停描述）。
 */
public final class PDCTitle implements ModInitializer {
	public static final String MOD_ID = "pdctitle";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Path CONFIG_DIR;
	public static TitleConfig CONFIG;
	public static TitleStore STORE;
	public static TitleService SERVICE;

	@Override
	public void onInitialize() {
		CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		CONFIG = new TitleConfig();
		CONFIG.load(CONFIG_DIR);
		STORE = new TitleStore(CONFIG_DIR);
		STORE.load();
		SERVICE = new TitleService(STORE);
		LOGGER.info("PDCTitle 初始化完成：称号池 {} 条，玩家记录 {} 条",
			STORE.definitionsSnapshot().size(), STORE.getOrCreateCount());
	}
}
