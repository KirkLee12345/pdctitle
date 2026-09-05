package net.kirklee.pdctitle.mixin;

import java.util.function.Predicate;
import net.kirklee.pdctitle.PDCTitle;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Invoker;

/**
 * 聊天拦截（总漏斗）：签名与未签名（第三方验证服/离线）的玩家聊天都会经过
 * PlayerList 私有 4 参 broadcastChatMessage。
 *
 * 佩戴称号时：取消原广播 → 以服务端系统行重发（称号块带悬停）。
 * 同时按原版逻辑补记服务端控制台/日志（logChatMessage + “Not Secure”判定），
 * 保证“服务端输出”与原版完全一致——只是玩家看到的行带称号前缀。
 *
 * 注意：26.2 未签名消息会被标 isSystem()==true，故不以 isSystem 判定放行。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListChatMixin {

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
		// 1) 按原版方式记录服务端日志/控制台（含 [Not Secure] 判定），保证服务端输出与原版一致
		MinecraftServer server = sender.level().getServer();
		boolean trusted = pdctitle$verifyChatTrusted(message);
		server.logChatMessage(message.decoratedContent(), params, trusted ? null : "Not Secure");
		// 2) 取消原广播，改为带称号/悬停的系统行
		ci.cancel();
		self.broadcastSystemMessage(line.get(), false);
	}

	/** 复用原版私有 verifyChatTrusted，判定日志是否带 “Not Secure” 前缀。 */
	@Invoker("verifyChatTrusted")
	abstract boolean pdctitle$verifyChatTrusted(PlayerChatMessage message);
}
