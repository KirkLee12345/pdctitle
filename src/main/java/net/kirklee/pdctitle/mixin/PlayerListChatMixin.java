package net.kirklee.pdctitle.mixin;

import java.util.function.Predicate;
import net.kirklee.pdctitle.PDCTitle;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 聊天拦截（总漏斗）：PlayerList 的两个公开 broadcastChatMessage 都委托到私有
 * 4 参方法；26.2 的签名与未签名（第三方验证服/离线）玩家聊天均会经过这里。
 *
 * 注意：未签名消息可能被标记为 isSystem()==true（无签名链），因此这里不再以
 * isSystem 判定是否放行，而只以“是否有真实发送者”为准，避免漏掉未签名聊天。
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
		PlayerList self = (PlayerList) (Object) this;
		String text = message.decoratedContent().getString();
		if (text.isEmpty()) return;
		var line = PDCTitle.SERVICE.chatLine(sender, text);
		if (line.isEmpty()) return;
		ci.cancel();
		self.broadcastSystemMessage(line.get(), false);
	}
}
