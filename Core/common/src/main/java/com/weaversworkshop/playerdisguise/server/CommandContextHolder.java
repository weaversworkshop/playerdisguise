package com.weaversworkshop.playerdisguise.server;

import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-local stack of the {@link CommandSourceStack} currently executing a Brigadier command on the server.
 * <p>Populated by {@code CommandsPerformMixin} around {@code Commands#performCommand}. Consumed by
 * {@code PlayerListMixin} to gate the disguise privacy-block: console and outranking ops can still target
 * a disguised player by real name; non-ops and lower-rank ops cannot.
 */
public final class CommandContextHolder {
    private static final ThreadLocal<Deque<CommandSourceStack>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private CommandContextHolder() {}

    public static void push(CommandSourceStack source) {
        STACK.get().push(source);
    }

    public static void pop() {
        Deque<CommandSourceStack> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
    }

    public static @Nullable CommandSourceStack current() {
        return STACK.get().peek();
    }
}
