package dev.rangefinder.betterheimerlite;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Minimal Minecraft status ping. It skips the players object instead of retaining sample names. */
final class MinecraftStatusPing {
    private static final int MAX_PACKET_BYTES = 1_000_000;

    private MinecraftStatusPing() {
    }

    static Optional<String> queryVersion(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(handshakeBytes);
            writeVarInt(handshake, 0);       // handshake packet id
            writeVarInt(handshake, -1);      // unknown protocol version: status only
            writeString(handshake, host);
            handshake.writeShort(port);
            writeVarInt(handshake, 1);       // next state: status
            writeFrame(out, handshakeBytes.toByteArray());

            ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
            DataOutputStream request = new DataOutputStream(requestBytes);
            writeVarInt(request, 0);         // status request packet id
            writeFrame(out, requestBytes.toByteArray());
            out.flush();

            int packetLength = readVarInt(in);
            if (packetLength < 1 || packetLength > MAX_PACKET_BYTES) return Optional.empty();
            int packetId = readVarInt(in);
            if (packetId != 0) return Optional.empty();
            int jsonLength = readVarInt(in);
            if (jsonLength < 1 || jsonLength > MAX_PACKET_BYTES || jsonLength > packetLength) return Optional.empty();
            byte[] bytes = in.readNBytes(jsonLength);
            if (bytes.length != jsonLength) return Optional.empty();

            return parseVersionWithoutPlayerSamples(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> parseVersionWithoutPlayerSamples(String json) {
        String versionName = null;
        boolean hasPlayersSection = false;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (field.equals("version")) {
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                        reader.skipValue();
                        continue;
                    }
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String versionField = reader.nextName();
                        if (versionField.equals("name") && reader.peek() == JsonToken.STRING) {
                            versionName = reader.nextString();
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                } else if (field.equals("players")) {
                    hasPlayersSection = reader.peek() == JsonToken.BEGIN_OBJECT;
                    reader.skipValue(); // deliberately do not parse or store player sample names
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (IOException | IllegalStateException exception) {
            return Optional.empty();
        }
        if (!hasPlayersSection || versionName == null || versionName.isBlank()) return Optional.empty();
        return Optional.of(versionName.replaceAll("[\\p{Cntrl}]", "").trim());
    }

    private static void writeFrame(DataOutputStream out, byte[] payload) throws IOException {
        writeVarInt(out, payload.length);
        out.write(payload);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = read & 0x7F;
            result |= value << (7 * numRead);
            numRead++;
            if (numRead > 5) throw new IOException("VarInt is too long.");
        } while ((read & 0x80) != 0);
        return result;
    }
}
