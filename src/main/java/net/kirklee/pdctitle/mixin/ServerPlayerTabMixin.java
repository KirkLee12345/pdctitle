package net.kirklee.pdctitle.mixin;

import net.kirklee.pdctitle.PDCTitle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * H2a：Tab 列表显示名来源。佩戴称号时返回 “称号 + 名字”，否则走原逻辑（null → 档案名）。
 * 玩家进服时的 ADD_PLAYER 条目会调用本方法，故新玩家首屏天然正确。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTabMixin {
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void pdctitle$tabName(CallbackInfoReturnable<Component> cir) {
		if (PDCTitle.SERVICE == null) return;
		ServerPlayer self = (ServerPlayer) (Object) this;
		PDCTitle.SERVICE.tabDisplayName(self).ifPresent(cir::setReturnValue);
	}
}
