package com.example.blockhighlighter.mixin.client;

import com.example.blockhighlighter.client.BlockHighlighterClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
abstract class ClientWorldMixin {
	@Inject(method = "handleBlockUpdate", at = @At("TAIL"))
	private void blockHighlighter$receivedBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		BlockHighlighterClient.blockUpdated((ClientWorld) (Object) this, pos);
	}

	// Predicted/local changes use this override; authoritative updates can call
	// the superclass directly, so handleBlockUpdate needs its own notification.
	@Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("RETURN"))
	private void blockHighlighter$changedBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
			CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			BlockHighlighterClient.blockUpdated((ClientWorld) (Object) this, pos);
		}
	}
}
