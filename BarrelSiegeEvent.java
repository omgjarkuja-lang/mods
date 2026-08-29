package ru.barrelsiege;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-only controller for the protected barrel event. */
public final class BarrelSiegeEvent {
    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static long intervalTicks = 15L * TICKS_PER_MINUTE;
    private static long nextEventAt = -1L;
    private static ActiveSiege active;

    private BarrelSiegeEvent() { }

    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        ServerLevel level = event.server().overworld();
        long now = level.getGameTime();
        if (nextEventAt < 0) nextEventAt = now + intervalTicks;

        if (active != null) {
            active.tick(level);
            return;
        }
        if (now >= nextEventAt) {
            start(level);
            nextEventAt = now + intervalTicks;
        }
    }

    public static void forceStart(ServerLevel level) {
        if (active == null) start(level);
    }

    public static void setIntervalMinutes(int minutes) {
        intervalTicks = minutes * TICKS_PER_MINUTE;
    }

    public static boolean onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (active != null && !active.unlocked && event.getLevel().dimension() == Level.OVERWORLD
            && active.barrelPos.equals(event.getPos())) {
            event.getEntity().displayClientMessage(Component.literal("§cБочка запечатана. Сначала победи охрану!"), true);
            return true;
        }
        return false;
    }

    public static boolean onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (active != null && !active.unlocked && event.getLevel().dimension() == Level.OVERWORLD
            && active.barrelPos.equals(event.getPos())) {
            return true;
        }
        return false;
    }

    private static void start(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        ServerPlayer chosen = players.get(level.random.nextInt(players.size()));
        BlockPos pos = findSurface(level, chosen, level.random);
        level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);
        active = new ActiveSiege(pos);
        announce(level, "§6[Barrel Siege] §eЗащищённая бочка появилась: §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        active.spawnWave(level);
    }

    private static BlockPos findSurface(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(129) - 64;
        int z = player.getBlockZ() + random.nextInt(129) - 64;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static void announce(ServerLevel level, String text) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    private static final class ActiveSiege {
        private final BlockPos barrelPos;
        private final List<UUID> guards = new ArrayList<>();
        private int wave = 1;
        private boolean unlocked;

        private ActiveSiege(BlockPos barrelPos) {
            this.barrelPos = barrelPos;
        }

        private void tick(ServerLevel level) {
            if (unlocked) return;
            if (!level.getBlockState(barrelPos).is(Blocks.BARREL)) {
                level.setBlock(barrelPos, Blocks.BARREL.defaultBlockState(), 3);
            }
            guards.removeIf(id -> {
                var entity = level.getEntity(id);
                return entity == null || !entity.isAlive();
            });
            if (!guards.isEmpty()) return;

            if (wave < 3) {
                wave++;
                spawnWave(level);
            } else {
                unlocked = true;
                fillLoot(level);
                announce(level, "§a[Barrel Siege] Охрана побеждена — бочка с топовым лутом открыта!");
            }
        }

        private void spawnWave(ServerLevel level) {
            EntityType<? extends Mob>[] mobs = switch (wave) {
                case 1 -> new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
                case 2 -> new EntityType[]{EntityType.HUSK, EntityType.DROWNED, EntityType.CAVE_SPIDER, EntityType.WITCH};
                default -> new EntityType[]{EntityType.VINDICATOR, EntityType.EVOKER, EntityType.BLAZE, EntityType.WITHER_SKELETON};
            };
            int amount = switch (wave) { case 1 -> 8; case 2 -> 12; default -> 16; };
            for (int i = 0; i < amount; i++) {
                EntityType<? extends Mob> type = mobs[level.random.nextInt(mobs.length)];
                Mob mob = type.create(level, EntitySpawnReason.EVENT);
                if (mob == null) continue;
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double radius = 5.0 + level.random.nextDouble() * 7.0;
                mob.moveTo(barrelPos.getX() + 0.5 + Math.cos(angle) * radius, barrelPos.getY(), barrelPos.getZ() + 0.5 + Math.sin(angle) * radius, level.random.nextFloat() * 360F, 0F);
                mob.setPersistenceRequired();
                level.addFreshEntity(mob);
                guards.add(mob.getUUID());
            }
            announce(level, "§c[Barrel Siege] Волна " + wave + "/3: уничтожь всех защитников!");
        }

        private void fillLoot(ServerLevel level) {
            if (!(level.getBlockEntity(barrelPos) instanceof BarrelBlockEntity barrel)) return;
            ItemStack[] loot = {
                new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
                new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS),
                new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.NETHERITE_PICKAXE),
                new ItemStack(Items.NETHERITE_AXE), new ItemStack(Items.NETHERITE_SHOVEL),
                new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW), new ItemStack(Items.SHIELD),
                new ItemStack(Items.ELYTRA), new ItemStack(Items.TOTEM_OF_UNDYING, 4),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 12), new ItemStack(Items.GOLDEN_CARROT, 64),
                new ItemStack(Items.NETHERITE_INGOT, 32), new ItemStack(Items.DIAMOND, 64),
                new ItemStack(Items.EMERALD, 64), new ItemStack(Items.DIAMOND_BLOCK, 32),
                new ItemStack(Items.OBSIDIAN, 64), new ItemStack(Items.ENDER_PEARL, 16),
                new ItemStack(Items.FIREWORK_ROCKET, 64), new ItemStack(Items.EXPERIENCE_BOTTLE, 32)
            };
            for (int i = 0; i < loot.length; i++) barrel.setItem(i, loot[i]);
            barrel.setChanged();
        }
    }
}
