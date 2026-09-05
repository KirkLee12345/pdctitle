package net.kirklee.pdctitle.mixin;

import net.kirklee.pdctitle.TitleCommands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * H5：命令注册（零 fabric-api）。
 * 26.2 命令类为 net.minecraft.commands.Commands；构造器签名
 * Commands(Commands.CommandSelection, CommandBuildContext)。在尾部向 Brigadier
 * dispatcher 注册本模组指令（幂等：已存在根则跳过）。
 */
@Mixin(Commands.class)
public abstract class CommandsRegistrationMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void pdctitle$register(Commands.CommandSelection selection, CommandBuildContext buildContext, CallbackInfo ci) {
		Commands self = (Commands) (Object) this;
		if (self.getDispatcher().getRoot().getChild("pdctitle") != null) return;
		TitleCommands.register(self.getDispatcher());
	}
}
