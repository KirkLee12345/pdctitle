package net.kirklee.pdctitle.mixin;

import net.kirklee.pdctitle.PDCTitle;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 兜底拦截：每个收件人收到玩家聊天的最终出口（sendPlayerChatMessage）。
 * 若该服（如第三方验证/离线）的玩家聊天不走 PlayerList 广播漏斗，仍会经过此出口，
 * 这里同样替换为带悬停称号的服务端系统行。发送者无佩戴/通道关闭时放行原版。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerChatSendMixin {
	@Inject(
		method = "sendPlayerChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/network/chat/ChatType$Bound;)V",
		at = @At("HEAD"),
		cancellable = true)
	private void pdctitle$rewriteOutgoing(PlayerChatMessage message, ChatType.Bound params, CallbackInfo ci) {
		if (PDCTitle.SERVICE == null || message.isSystem()) return;
		ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
		ServerPlayer recipient = self.getPlayer();
		if (recipient == null) return;
		MinecraftServer server = recipient.level().getServer();
		ServerPlayer sender = server.getPlayerList().getPlayer(message.sender());
		if (sender == null) return;
		String text = message.decoratedContent().getString();
		var line = PDCTitle.SERVICE.chatLine(sender, text);
		if (line.isEmpty()) return;
		ci.cancel();
		self.send(new ClientboundSystemChatPacket(line.get(), false));
	}
}
