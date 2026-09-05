package net.kirklee.pdctitle.mixin;

import java.util.function.Predicate;
import net.kirklee.pdctitle.PDCTitle;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 聊天拦截（总漏斗）：PlayerList 的两个公开 broadcastChatMessage 都委托到私有
 * 4 参方法；26.2 的签名/未签名玩家聊天均会经过这里。佩戴称号时取消原广播，
 * 改以服务端系统行重发（称号块带悬停描述）。无佩戴/通道关闭则原样放行。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListChatMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("pdctitle-chat");

	@Inject(
		method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
		at = @At("HEAD"),
		cancellable = true)
	private void pdctitle$rewriteChat(PlayerChatMessage message, Predicate<ServerPlayer> predicate,
			ServerPlayer sender, ChatType.Bound params, CallbackInfo ci) {
		if (PDCTitle.SERVICE == null || message.isSystem() || sender == null) return;
		PlayerList self = (PlayerList) (Object) this;
		String text = message.decoratedContent().getString();
		var line = PDCTitle.SERVICE.chatLine(sender, text);
		if (line.isEmpty()) return;
		ci.cancel();
		self.broadcastSystemMessage(line.get(), false);
		LOGGER.info("<{}> {}", sender.getGameProfile().name(), text);
	}
}
