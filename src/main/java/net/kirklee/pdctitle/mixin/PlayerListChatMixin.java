package net.kirklee.pdctitle.mixin;

import net.kirklee.pdctitle.PDCTitle;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
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
 * H3：聊天拦截重发。
 * 1.19.1+ 聊天签名使服务端无法改写玩家消息的显示名；本模组在有佩戴称号时取消原广播，
 * 改为以系统消息重发自组装行（称号块带悬停描述）。无佩戴/通道关闭时原样放行。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListChatMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("pdctitle-chat");

	@Inject(
		method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
		at = @At("HEAD"),
		cancellable = true)
	private void pdctitle$rewriteChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params, CallbackInfo ci) {
		if (PDCTitle.SERVICE == null || message.isSystem()) return;
		String text = message.decoratedContent().getString();
		PlayerList self = (PlayerList) (Object) this;
		PDCTitle.SERVICE.chatLine(sender, text).ifPresent(line -> {
			ci.cancel();
			self.broadcastSystemMessage(line, false);
			LOGGER.info("<{}> {}", sender.getGameProfile().name(), text);
		});
	}
}
