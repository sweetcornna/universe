package io.github.sweetcornna.universe.factory;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * 工厂锚（ADR-0015）：让以它为中心的 7×7 个区块一直加载，这颗星球的维度也就不会卸载，人走了工厂照样开工。
 * 不耗电，靠造价限制数量。票据记在原版的存档里，重启服务器后照样生效；拆掉时撤掉。
 */
public class FactoryAnchorBlock extends BaseEntityBlock {
    /** 以所在区块为中心，往四边各 3 个区块。 */
    public static final int RADIUS = 3;
    public static final TicketController TICKETS = new TicketController(Universe.id("factory_anchor"), (level, helper) -> {
        // 读档时撤掉方块已经不在了的票据（比如被指令替换掉）
        for (BlockPos owner : List.copyOf(helper.getBlockTickets().keySet())) {
            if (!(level.getBlockState(owner).getBlock() instanceof FactoryAnchorBlock)) {
                helper.removeAllTickets(owner);
            }
        }
    });

    public FactoryAnchorBlock(Properties properties) {
        super(properties);
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterTicketControllersEvent.class, event -> event.register(TICKETS));
    }

    /** 加上或撤掉这个工厂锚的全部票据。 */
    static void force(ServerLevel level, BlockPos pos, boolean add) {
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                TICKETS.forceChunk(level, pos, center.x() + dx, center.z() + dz, add, false);
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactoryAnchorBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && !level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.universe.factory_anchor.placed", RADIUS * 2 + 1));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            int side = RADIUS * 2 + 1;
            player.sendOverlayMessage(Component.translatable("message.universe.factory_anchor.info", side, side * 16));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.05,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0, 0.03 + random.nextDouble() * 0.03, 0);
        }
    }
}
