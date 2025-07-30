package com.smd.mca.util;

import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.smd.mca.core.MCA;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Util {
    private static final String RESOURCE_PREFIX = "assets/mca/";

    /**
     * 获取地面以上安全出生点（第一个空气方块上方）。
     */
    public static int getSpawnSafeTopLevel(World world, int x, int y, int z) {
        BlockPos pos;
        IBlockState state;

        while (y > 0) {
            pos = new BlockPos(x, y, z);
            state = world.getBlockState(pos);
            if (!state.getBlock().isAir(state, world, pos)) {
                break;
            }
            y--;
        }

        return y + 1;
    }

    /**
     * 从 mod JAR 中读取资源为字符串。
     */
    public static String readResource(String path) {
        String location = RESOURCE_PREFIX + path;

        try (InputStreamReader reader = new InputStreamReader(
                MCA.class.getClassLoader().getResourceAsStream(location))) {
            return IOUtils.toString(reader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource from JAR: " + location, e);
        }
    }

    /**
     * 将资源内容解析为 JSON 对象。
     */
    public static <T> T readResourceAsJSON(String path, Class<T> type) {
        Gson gson = new Gson();
        return gson.fromJson(readResource(path), type);
    }

    /**
     * 通过 UUID 获取实体。
     */
    public static Optional<Entity> getEntityByUUID(World world, UUID uuid) {
        for (Entity entity : world.loadedEntityList) {
            if (uuid.equals(entity.getUniqueID())) {
                return Optional.of(entity);
            }
        }
        return Optional.absent();
    }

    /**
     * 获取指定类型的 UUID 对应实体。
     */
    public static <T extends Entity> Optional<T> getEntityByUUID(World world, UUID uuid, Class<? extends T> clazz) {
        for (Entity entity : world.loadedEntityList) {
            if (clazz.isAssignableFrom(entity.getClass()) && uuid.equals(entity.getUniqueID())) {
                return Optional.of((T) entity);
            }
        }
        return Optional.absent();
    }

    /**
     * 获取附近指定距离内所有符合条件的方块坐标。
     */
    public static List<BlockPos> getNearbyBlocks(BlockPos origin, World world, @Nullable Class<? extends Block> filter, int xzDist, int yDist) {
        List<BlockPos> result = new ArrayList<>();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        for (int x = -xzDist; x <= xzDist; x++) {
            for (int y = -yDist; y <= yDist; y++) {
                for (int z = -xzDist; z <= xzDist; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
                    Block block = world.getBlockState(pos).getBlock();

                    if (filter == null || filter.isAssignableFrom(block.getClass())) {
                        result.add(pos);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 获取距离 origin 最近的方块位置。
     */
    public static BlockPos getNearestPoint(BlockPos origin, List<BlockPos> blocks) {
        BlockPos nearest = null;
        double minDistSq = Double.MAX_VALUE;

        for (BlockPos target : blocks) {
            double distSq = origin.distanceSq(target);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = target;
            }
        }

        return nearest;
    }
}
