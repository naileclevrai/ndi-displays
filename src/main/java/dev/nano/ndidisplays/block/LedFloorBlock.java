package dev.nano.ndidisplays.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * Walkable LED floor tile. FACING is the direction of the image's top (v=0).
 * Adjacent tiles of the same facing merge into one video rectangle, like a wall.
 *
 * The same class hung from a ceiling is a slab at the top of the block, picture
 * facing down into the room. Floor and ceiling tiles do not merge with each other.
 */
public class LedFloorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Slim slab, 2 pixels thick — collision matches the visual cabinet. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);
    /** Same slab, stuck to the ceiling. */
    private static final VoxelShape CEILING_SHAPE = Block.box(0, 14, 0, 16, 16, 16);

    private final boolean ceiling;

    public LedFloorBlock(Properties properties) {
        this(properties, false);
    }

    public LedFloorBlock(Properties properties, boolean ceiling) {
        super(properties);
        this.ceiling = ceiling;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean isCeiling() {
        return ceiling;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Clicking the underside of a block, or the side of a tile already on the ceiling,
        // hangs the next tile up there. Anywhere else it stays a walkable floor.
        Direction face = ctx.getClickedFace();
        BlockState clicked = ctx.getLevel().getBlockState(ctx.getClickedPos());
        boolean ontoCeiling = face == Direction.DOWN
                || (face.getAxis().isHorizontal()
                && clicked.getBlock() instanceof LedFloorBlock existing
                && existing.ceiling);
        LedFloorBlock block = ontoCeiling
                ? (LedFloorBlock) dev.nano.ndidisplays.NdiDisplays.LED_CEILING.get()
                : (LedFloorBlock) dev.nano.ndidisplays.NdiDisplays.LED_FLOOR.get();
        // Image top points the way the player is looking, like a floor processor's orientation.
        return block.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return ceiling ? CEILING_SHAPE : SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return ceiling ? CEILING_SHAPE : SHAPE;
    }

    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        // Middle-click on a ceiling tile still gives the floor item, which is what places it.
        return new net.minecraft.world.item.ItemStack(dev.nano.ndidisplays.NdiDisplays.LED_FLOOR_ITEM.get());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof dev.nano.ndidisplays.item.NdiConfigCardItem) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof LedFloorBlockEntity clicked) {
                String source = dev.nano.ndidisplays.item.NdiConfigCardItem
                        .storedSource(player.getItemInHand(hand));
                for (BlockPos tilePos : FloorScanner.collectGroup(level, pos, clicked.getFacing(),
                        clicked.getPanelKind())) {
                    if (level.getBlockEntity(tilePos) instanceof LedFloorBlockEntity tile) {
                        tile.applyNdiCard(source);
                        BlockState tileState = level.getBlockState(tilePos);
                        level.sendBlockUpdated(tilePos, tileState, tileState, 3);
                    }
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "item.ndidisplays.ndi_config_card.applied", source), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (DmxScreen.isTheatricalCard(player.getItemInHand(hand))) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof LedFloorBlockEntity clicked) {
                DmxScreen.applyTheatricalCard(level, pos, state, player,
                        player.getItemInHand(hand), clicked);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    dev.nano.ndidisplays.client.ClientHooks.openFloorConfig(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LedFloorBlockEntity(pos, state);
    }
}
