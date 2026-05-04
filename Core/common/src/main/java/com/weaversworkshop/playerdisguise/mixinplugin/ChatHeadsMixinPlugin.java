package com.weaversworkshop.playerdisguise.mixinplugin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ChatHeadsMixinPlugin implements IMixinConfigPlugin {
    private static final boolean CHAT_HEADS_PRESENT;

    static {
        // Probe via classloader resource — Class.forName would *define* the class,
        // preventing mixin from transforming it ("loaded too early").
        ClassLoader cl = ChatHeadsMixinPlugin.class.getClassLoader();
        boolean present = cl.getResource("dzwdz/chat_heads/ChatHeads.class") != null;
        CHAT_HEADS_PRESENT = present;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return CHAT_HEADS_PRESENT; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
