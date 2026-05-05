package com.weaversworkshop.playerdisguise.neoforge.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.AliasRegistry.NameInterval;
import com.weaversworkshop.playerdisguise.server.LookupFormatter;
import com.weaversworkshop.playerdisguise.server.ReportLog;
import net.minecraft.world.entity.Entity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@EventBusSubscriber(modid = PlayerDisguise.MODID)
public final class PdCommands {
    private PdCommands() {}

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildWhois());
        event.getDispatcher().register(buildNameHistory());
        event.getDispatcher().register(buildReportDisguise());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildWhois() {
        return Commands.literal("whois")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> runWhois(ctx.getSource(), StringArgumentType.getString(ctx, "name"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildNameHistory() {
        return Commands.literal("namehistory")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> runNameHistory(ctx.getSource(), StringArgumentType.getString(ctx, "name"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildReportDisguise() {
        return Commands.literal("reportdisguise")
                .then(Commands.argument("alias", StringArgumentType.word())
                        .executes(ctx -> runReport(ctx.getSource(), StringArgumentType.getString(ctx, "alias"), ""))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> runReport(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "alias"),
                                        StringArgumentType.getString(ctx, "reason")))));
    }

    // ---- /reportdisguise -------------------------------------------------------

    private static int runReport(CommandSourceStack source, String alias, String reason) {
        AliasRegistry reg = AliasRegistry.get();
        Entity entity = source.getEntity();
        if (entity == null) {
            source.sendFailure(Component.literal("/reportdisguise can only be used by players."));
            return 0;
        }
        UUID reporterUuid = entity.getUUID();
        String reporterReal = reg.realNameOf(reporterUuid);
        if (reporterReal == null) reporterReal = source.getTextName();

        UUID targetUuid = reg.uuidOf(alias);
        if (targetUuid == null) {
            source.sendFailure(Component.literal("No player is currently using the alias '" + alias + "'."));
            return 0;
        }
        String targetReal = reg.realNameOf(targetUuid);
        if (targetReal == null) targetReal = "?";

        boolean ok = ReportLog.get().append(reporterUuid, reporterReal, targetUuid, targetReal, alias,
                reason == null ? "" : reason);
        if (!ok) {
            source.sendFailure(Component.literal("Failed to record report. Tell an administrator."));
            return 0;
        }

        PlayerDisguise.LOGGER.info("Disguise report: reporter={} target={} alias='{}' reason='{}'",
                reporterReal, targetReal, alias, reason == null ? "" : reason);
        source.sendSuccess(() -> Component.literal("Report submitted. Thank you.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ---- /whois ----------------------------------------------------------------

    private static int runWhois(CommandSourceStack source, String name) {
        AliasRegistry reg = AliasRegistry.get();
        MinecraftServer server = source.getServer();
        int runnerOp = opLevelOfRunner(source);

        LookupFormatter.WhoisResult result = LookupFormatter.whois(
                reg.holdersOfAlias(name),
                runnerOp,
                uuid -> opLevelOfUuid(server, uuid),
                reg::realNameOf);

        sendHeader(source, "/whois", name);
        if (result.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  (no holders)").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        sendColumnHeader(source, "Player", "Held from", "Held until");
        for (LookupFormatter.WhoisRow r : result.rows()) {
            MutableComponent row = rowAlias(r.displayName(), r.interval());
            source.sendSuccess(() -> row, false);
        }
        return result.rows().size();
    }

    // ---- /namehistory ----------------------------------------------------------

    private static int runNameHistory(CommandSourceStack source, String name) {
        AliasRegistry reg = AliasRegistry.get();
        MinecraftServer server = source.getServer();
        int runnerOp = opLevelOfRunner(source);

        UUID target = reg.resolveTarget(name);
        if (target == null) {
            source.sendFailure(Component.literal("No player has ever held the name '" + name + "'."));
            return 0;
        }

        LookupFormatter.NameHistoryResult result = LookupFormatter.nameHistory(
                target,
                runnerOp,
                uuid -> opLevelOfUuid(server, uuid),
                reg::realNameOf,
                reg::aliasOf,
                reg::historyOf,
                name);

        sendHeader(source, "/namehistory", name);
        String shownReal = result.shownRealName();
        source.sendSuccess(() -> Component.literal("  Real name: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(shownReal).withStyle(ChatFormatting.WHITE)), false);
        if (!result.hasHistory()) {
            source.sendSuccess(() -> Component.literal("  (no aliases on record)").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        sendColumnHeader(source, "Alias", "Held from", "Held until");
        for (NameInterval ni : result.rows()) {
            MutableComponent row = rowAliasOnly(ni);
            source.sendSuccess(() -> row, false);
        }
        return result.rows().size();
    }

    // ---- Row builders ----------------------------------------------------------

    private static MutableComponent rowAlias(String holderRealName, NameInterval ni) {
        MutableComponent row = Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(holderRealName).withStyle(ChatFormatting.WHITE));
        if (ni.realName()) {
            row.append(Component.literal(" (real)").withStyle(ChatFormatting.AQUA));
        }
        return row.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(formatTs(ni.startMs())).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  →  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(ni.isOpen()
                        ? Component.literal("now").withStyle(ChatFormatting.GREEN)
                        : Component.literal(formatTs(ni.endMs())).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent rowAliasOnly(NameInterval ni) {
        MutableComponent row = Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(ni.alias()).withStyle(ni.realName() ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        if (ni.realName()) {
            row.append(Component.literal(" (real)").withStyle(ChatFormatting.AQUA));
        }
        return row.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(formatTs(ni.startMs())).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  →  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(ni.isOpen()
                        ? Component.literal("now").withStyle(ChatFormatting.GREEN)
                        : Component.literal(formatTs(ni.endMs())).withStyle(ChatFormatting.GRAY));
    }

    private static void sendHeader(CommandSourceStack source, String cmd, String arg) {
        source.sendSuccess(() -> Component.literal("─── " + cmd + " \"" + arg + "\" ───").withStyle(ChatFormatting.GOLD), false);
    }

    private static void sendColumnHeader(CommandSourceStack source, String c1, String c2, String c3) {
        source.sendSuccess(() -> Component.literal("  ")
                .append(Component.literal(c1).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_AQUA))
                .append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(c2).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_AQUA))
                .append(Component.literal("        ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(c3).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_AQUA)), false);
    }

    private static String formatTs(long ms) {
        return TS_FMT.format(Instant.ofEpochMilli(ms));
    }

    // ---- Op-level helpers ------------------------------------------------------

    private static int opLevelOfRunner(CommandSourceStack source) {
        // Console / command blocks effectively run at level 4.
        if (source.getEntity() == null) return 4;
        for (int i = 4; i >= 1; i--) if (source.hasPermission(i)) return i;
        return 0;
    }

    private static int opLevelOfUuid(MinecraftServer server, UUID uuid) {
        String realName = AliasRegistry.get().realNameOf(uuid);
        GameProfile profile = new GameProfile(uuid, realName == null ? "" : realName);
        ServerOpListEntry entry = server.getPlayerList().getOps().get(profile);
        return entry == null ? 0 : entry.getLevel();
    }
}
