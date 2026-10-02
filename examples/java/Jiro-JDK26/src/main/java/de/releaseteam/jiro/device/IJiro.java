package de.releaseteam.jiro.device;

import de.releaseteam.jiro.protocol.FieldSchema;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public interface IJiro {
    int registerField(String name, int ftype, int role) throws IOException;

    int registerField(String name, int ftype, int role, Integer fieldId) throws IOException;

    void onUpdate(String name, Consumer<Object> callback);

    void setValue(String name, Object value) throws IOException;

    void showText(String text) throws IOException;

    void showFields(String... names) throws IOException;

    void sendFrame(int ptype, byte[] payload) throws IOException;

    void start() throws IOException;

    void stop();

    boolean isRunning();

    Map<Integer, FieldSchema> getSchema();

    Map<String, Integer> getNameToId();

    void handleConfig(byte[] payload);

    void handleData(byte[] payload);
}
