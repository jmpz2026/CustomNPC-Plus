package noppes.npcs.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.controllers.ProfileController;
import noppes.npcs.controllers.data.profile.EnumProfileOperation;
import noppes.npcs.controllers.data.profile.Profile;
import noppes.npcs.controllers.data.profile.ProfileOperation;
import noppes.npcs.controllers.data.profile.Slot;

/**
 * point 10: player-facing slot command. Top-level "/profile" (no op required),
 * modelled on CmdMoney. Attach in CustomNpcs.registerCommand:
 *     e.getDispatcher().register(CmdProfile.register());
 */
public class CmdProfile {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("profile")
            .requires(source -> true)
            .then(Commands.literal("list").executes(CmdProfile::list))
            .then(Commands.literal("create").executes(CmdProfile::create))
            .then(Commands.literal("change")
                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                    .executes(CmdProfile::change)))
            .then(Commands.literal("remove")
                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                    .executes(CmdProfile::remove)));
    }

    private static int change(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        report(ctx, ProfileController.Instance.changeSlot(player, IntegerArgumentType.getInteger(ctx, "slot")));
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        report(ctx, ProfileController.Instance.createSlot(player));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        report(ctx, ProfileController.Instance.removeSlot(player, IntegerArgumentType.getInteger(ctx, "slot")));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        Profile profile = ProfileController.Instance.getProfile(player);
        if (profile == null) return 0;
        ctx.getSource().sendSuccess(() -> Component.literal("Profile slots:"), false);
        for (Slot slot : profile.getSlots().values()) {
            final int id = slot.getId();
            final String label = " - [" + id + "] " + slot.getName()
                + (id == profile.getCurrentSlotId() ? " (active)" : "");
            ctx.getSource().sendSuccess(() -> Component.literal(label), false);
        }
        return 1;
    }

    private static void report(CommandContext<CommandSourceStack> ctx, ProfileOperation op) {
        if (op.getResult() == EnumProfileOperation.SUCCESS)
            ctx.getSource().sendSuccess(() -> Component.literal(op.getMessage()), false);
        else
            ctx.getSource().sendFailure(Component.literal(op.getMessage()));
    }
}
