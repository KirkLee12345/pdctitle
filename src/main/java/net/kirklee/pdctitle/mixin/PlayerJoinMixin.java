package net.kirklee.pdctitle.mixin;

import net.kirklee.pdctitle.PDCTitle;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * H4：玩家加入后套用称号（记住名字、名牌队伍、Tab 兜底刷新）。
 */
@Mixin(PlayerList.class)
public abstract class PlayerJoinMixin {
	@Inject(method = "placeNewPlayer", at = @At("TAIL"))
	private void pdctitle$onJoin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
		if (PDCTitle.SERVICE == null) return;
		PDCTitle.SERVICE.onPlayerJoin(player.level().getServer(), player);
	}
}
