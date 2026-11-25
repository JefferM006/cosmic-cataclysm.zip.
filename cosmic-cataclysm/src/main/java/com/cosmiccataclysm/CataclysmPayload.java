package com.cosmiccataclysm;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Paquete servidor -> cliente: "empieza un cataclismo aquí". El cliente anima el cielo por su cuenta. */
public record CataclysmPayload(double x, double y, double z, long seed) implements CustomPayload {
    public static final Id<CataclysmPayload> ID =
            new Id<>(Identifier.of(CosmicCataclysm.MOD_ID, "cataclysm"));

    public static final PacketCodec<RegistryByteBuf, CataclysmPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, CataclysmPayload::x,
            PacketCodecs.DOUBLE, CataclysmPayload::y,
            PacketCodecs.DOUBLE, CataclysmPayload::z,
            PacketCodecs.VAR_LONG, CataclysmPayload::seed,
            CataclysmPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
