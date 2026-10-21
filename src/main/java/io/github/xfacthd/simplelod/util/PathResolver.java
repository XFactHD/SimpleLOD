package io.github.xfacthd.simplelod.util;

import com.google.common.hash.Hashing;
import io.github.xfacthd.simplelod.SimpleLOD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/// Based on file path computation for client-local session data proposed in [NeoForge#3679](https://github.com/neoforged/NeoForge/pull/3679).
public final class PathResolver {
    private static final int MAX_SERVER_NAME_LENGTH = 64;
    private static final int SERVER_NAME_HASH_LENGTH = 8;

    public static Path getStoragePath(ClientLevel level) {
        String levelFolder = level.dimension().identifier().toDebugFileName();
        return getInstancePath(level).resolve(levelFolder);
    }

    private static Path getInstancePath(ClientLevel level) {
        IntegratedServer singleplayerServer = Minecraft.getInstance().getSingleplayerServer();
        if (singleplayerServer != null) {
            return singleplayerServer.getWorldPath(LevelResource.DATA).resolve(SimpleLOD.MOD_ID);
        }

        ServerData serverData = level.connection.getServerData();
        if (serverData != null) {
            return FMLPaths.GAMEDIR.get().resolve(SimpleLOD.MOD_ID).resolve(getServerFolder(serverData));
        }

        throw new IllegalStateException("Cannot determine world type (SP vs. MP)");
    }

    private static String getServerFolder(ServerData serverData) {
        String folderName = switch (serverData.type()) {
            case REALM -> "realm_" + serverData.name;
            case LAN -> "lan_" + ServerAddress.parseString(serverData.ip).getHost() + "_" + serverData.name;
            case OTHER -> {
                if (!ServerAddress.isValidAddress(serverData.ip)) {
                    yield serverData.ip;
                }
                ServerAddress address = ServerAddress.parseString(serverData.ip);
                yield address.getHost() + "_" + address.getPort();
            }
        };
        String sanitizedFolderName = Util.sanitizeName(folderName, c -> c != '/' && Identifier.validPathChar(c));
        if (sanitizedFolderName.length() <= MAX_SERVER_NAME_LENGTH) {
            return sanitizedFolderName;
        }
        return sanitizedFolderName.substring(0, MAX_SERVER_NAME_LENGTH - SERVER_NAME_HASH_LENGTH - 1) + "-" + hashName(sanitizedFolderName);
    }

    @SuppressWarnings("deprecation")
    private static String hashName(String name) {
        return Hashing.sha1().hashUnencodedChars(name).toString().substring(0, SERVER_NAME_HASH_LENGTH);
    }

    private PathResolver() { }
}
