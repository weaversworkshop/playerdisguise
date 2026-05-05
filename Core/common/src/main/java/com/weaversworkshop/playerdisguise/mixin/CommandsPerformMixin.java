package com.weaversworkshop.playerdisguise.mixin;

import com.mojang.brigadier.ParseResults;
import com.weaversworkshop.playerdisguise.server.CommandContextHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsPerformMixin {

    @Inject(method = "performCommand", at = @At("HEAD"))
    private void playerdisguise$pushSource(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        CommandContextHolder.push(parse.getContext().getSource());
    }

    @Inject(method = "performCommand", at = @At("RETURN"))
    private void playerdisguise$popSource(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        CommandContextHolder.pop();
    }
}
