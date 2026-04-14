package org.zoteteam.height_patch;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkData;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ThreadCache;
import com.reider745.api.hooks.TypeHook;
import com.reider745.api.hooks.annotation.Hooks;
import com.reider745.api.hooks.annotation.Inject;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Consumer;

@Hooks(className = "cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer")
public class NetworkChunkSerializerHook {
    @Inject(type = TypeHook.RETURN)
    public static void serialize(IntSet protocols, BaseChunk chunk, Consumer<NetworkChunkSerializer.NetworkChunkSerializerCallback> callback, boolean antiXray, DimensionData dimensionData) {
        for (int protocolId : protocols) {
            byte[] blockEntities;
            if (chunk.getBlockEntities().isEmpty()) {
                blockEntities = new byte[0];
            } else {
                blockEntities = serializeEntities(chunk, protocolId);
            }

            int subChunkCount = 0;
            ChunkSection[] sections = chunk.getSections();
            for (int i = sections.length - 1; i >= 0; i--) {
                if (!sections[i].isEmpty()) {
                    subChunkCount = i + 1;
                    break;
                }
            }

            BinaryStream stream = ThreadCache.binaryStream.get().reset();
            NetworkChunkData networkChunkData = new NetworkChunkData(protocolId, subChunkCount, antiXray, dimensionData);

            subChunkCount = Math.max(1, subChunkCount);
            networkChunkData.setChunkSections(subChunkCount);

            BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(protocolId);
            //int offset = chunk.getSectionOffset();
            for (int i = 0; i < subChunkCount; i++) {
                sections[i].writeTo(protocolId, stream, antiXray, palette);
            }

            stream.put(chunk.getBiomeIdArray());

            // Border blocks
            stream.putByte((byte) 0);
            stream.put(blockEntities);

            callback.accept(new NetworkChunkSerializer.NetworkChunkSerializerCallback(protocolId, stream, networkChunkData.getChunkSections()));
        }
    }

    private static byte[] serializeEntities(BaseChunk chunk, int protocol) {
        List<CompoundTag> tagList = new ObjectArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BlockEntitySpawnable) {
                tagList.add(((BlockEntitySpawnable) blockEntity).getSpawnCompound(protocol));
            }
        }

        try {
            return NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
