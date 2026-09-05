package net.kirklee.pdctitle;

import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
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
 * 其余（玩家加入/聊天拦截/Tab/指令注册）由 Mixin 调用本类的静态单例。
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
		registerChatRewrite();
		LOGGER.info("PDCTitle 初始化完成：称号池 {} 条，玩家记录 {} 条",
			STORE.definitionsSnapshot().size(), STORE.getOrCreateCount());
	}

	/**
	 * 聊天拦截（fabric-message-api 官方事件路径）：佩戴称号时取消原签名消息，
	 * 以服务端系统行重发（称号块带悬停描述）。无佩戴/通道关闭则放行原版聊天。
	 */
	private static void registerChatRewrite() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (SERVICE == null || message.isSystem()) return true;
			String text = message.decoratedContent().getString();
			Optional<Component> line = SERVICE.chatLine(sender, text);
			if (line.isEmpty()) return true;
			sender.level().getServer().getPlayerList().broadcastSystemMessage(line.get(), false);
			LOGGER.info("[pdctitle-chat] <{}> {}", sender.getGameProfile().name(), text);
			return false; // 取消原消息广播
		});
	}
}
