package ru.barrelsiege;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(BarrelSiegeMod.MOD_ID)
public final class BarrelSiegeMod {
    public static final String MOD_ID = "barrelsiege";

    public BarrelSiegeMod() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(BarrelSiegeEvent::onServerTick);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(BarrelSiegeEvent::onRightClick);
        PlayerInteractEvent.LeftClickBlock.BUS.addListener(BarrelSiegeEvent::onLeftClick);
        RegisterCommandsEvent.BUS.addListener(BarrelSiegeMod::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("barrelsiege")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("start").executes(context -> {
                BarrelSiegeEvent.forceStart(context.getSource().getServer().overworld());
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Barrel Siege started."), true);
                return 1;
            }))
            .then(Commands.literal("interval").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
                .executes(context -> {
                    BarrelSiegeEvent.setIntervalMinutes(IntegerArgumentType.getInteger(context, "minutes"));
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Interval updated."), true);
                    return 1;
                }))));
    }
}
