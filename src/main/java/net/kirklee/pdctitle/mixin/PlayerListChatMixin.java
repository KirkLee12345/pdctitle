package net.kirklee.pdctitle.mixin;

import java.util.function.Predicate;
import net.kirklee.pdctitle.PDCTitle;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 聊天拦截（总漏斗）：签名与未签名（第三方验证服/离线）玩家聊天都会经过
 * PlayerList 私有 4 参 broadcastChatMessage。
 *
 * 佩戴称号时：取消原广播 → 向每个在线玩家单独发送带称号/悬停的系统行
 * （不经过广播，避免服务端重复把该行打进控制台）。
 *
 * 服务端控制台/日志：按原版文本格式补打一行（<玩家原名> 内容 + [Not Secure] 判定），
 * 发送者用 profile 原名、不经 ChatType 装饰，确保与原版纯玩家聊天记录一致。
 *
 * 注意：26.2 未签名消息会被标 isSystem()==true，故不以 isSystem 判定放行。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListChatMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("pdctitle");

	@Inject(
		method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
		at = @At("HEAD"),
		cancellable = true)
	private void pdctitle$rewriteChat(PlayerChatMessage message, Predicate<ServerPlayer> predicate,
			ServerPlayer sender, ChatType.Bound params, CallbackInfo ci) {
		if (PDCTitle.SERVICE == null || sender == null) return;
		String text = message.decoratedContent().getString();
		if (text.isEmpty()) return;
		var line = PDCTitle.SERVICE.chatLine(sender, text);
		if (line.isEmpty()) return;

		PlayerList self = (PlayerList) (Object) this;
		MinecraftServer server = sender.level().getServer();
		// 1) 服务端日志：原版文本格式（发送者用 profile 原名，不参与 ChatType 装饰）
		boolean trusted = pdctitle$verifyChatTrusted(message);
		String name = sender.getGameProfile().name();
		if (trusted) {
			LOGGER.info("<{}> {}", name, text);
		} else {
			LOGGER.info("[Not Secure] <{}> {}", name, text);
		}
		// 2) 取消原广播
		ci.cancel();
		// 3) 给每个在线玩家单独发带称号/悬停的系统行（不再走 broadcastSystemMessage，避免控制台重复行）
		Component out = line.get();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(out, false);
		}
	}

	/** 复用原版私有 verifyChatTrusted，判定日志是否带 “Not Secure” 前缀。 */
	@Invoker("verifyChatTrusted")
	abstract boolean pdctitle$verifyChatTrusted(PlayerChatMessage message);
}
