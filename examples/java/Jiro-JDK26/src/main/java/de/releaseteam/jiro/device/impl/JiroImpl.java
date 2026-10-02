package de.releaseteam.jiro.device.impl;

import de.releaseteam.jiro.device.IJiro;
import de.releaseteam.jiro.protocol.FieldSchema;
import de.releaseteam.jiro.device.ISerialPort;
import de.releaseteam.jiro.protocol.Protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class JiroImpl implements IJiro {
    private final ISerialPort port;
    private final Map<Integer, FieldSchema> schema = new ConcurrentHashMap<>();
    private final Map<String, Integer> nameToId = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Object>>> callbacks = new ConcurrentHashMap<>();

    private int nextId = 0;
    private volatile boolean running = false;
    private Thread readerThread;

    public JiroImpl(String portName, int baudRate) {
        this(new SerialPortImpl(portName, baudRate));
    }

    public JiroImpl(String portName) {
        this(portName, 115200);
    }

    public JiroImpl(ISerialPort port) {
        this.port = Objects.requireNonNull(port, "port cannot be null");
    }

    public ISerialPort getPort() {
        return port;
    }

    @Override
    public synchronized int registerField(String name, int ftype, int role) throws IOException {
        int fid = nextId++;
        return registerField(name, ftype, role, fid);
    }

    @Override
    public synchronized int registerField(String name, int ftype, int role, Integer fieldId) throws IOException {
        int fid = (fieldId != null) ? fieldId : nextId++;
        schema.put(fid, new FieldSchema(fid, name, ftype, role));
        nameToId.put(name, fid);

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[4 + nameBytes.length];
        payload[0] = (byte) fid;
        payload[1] = (byte) ftype;
        payload[2] = (byte) role;
        payload[3] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, payload, 4, nameBytes.length);

        sendFrame(Protocol.PKT_REGISTER, payload);
        return fid;
    }

    @Override
    public void onUpdate(String name, Consumer<Object> callback) {
        callbacks.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    @Override
    public void setValue(String name, Object value) throws IOException {
        Integer fid = nameToId.get(name);
        if (fid == null) {
            throw new IllegalArgumentException("Unknown field: " + name);
        }
        FieldSchema field = schema.get(fid);
        if (field == null) {
            throw new IllegalArgumentException("Unknown field id: " + fid);
        }
        byte[] raw = Protocol.encodeValue(field.getType(), value);
        byte[] payload = new byte[3 + raw.length];
        payload[0] = 1; // count
        payload[1] = (byte) (int) fid;
        payload[2] = (byte) raw.length;
        System.arraycopy(raw, 0, payload, 3, raw.length);

        sendFrame(Protocol.PKT_CONTROL, payload);
    }

    @Override
    public void showText(String text) throws IOException {
        if (text == null) {
            text = "";
        }
        if (text.length() > 60) {
            text = text.substring(0, 60);
        }
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        if (textBytes.length > 60) {
            textBytes = Arrays.copyOf(textBytes, 60);
        }
        byte[] payload = new byte[2 + textBytes.length];
        payload[0] = (byte) Protocol.DISPLAY_TEXT;
        payload[1] = (byte) textBytes.length;
        System.arraycopy(textBytes, 0, payload, 2, textBytes.length);

        sendFrame(Protocol.PKT_DISPLAY, payload);
    }

    @Override
    public void showFields(String... names) throws IOException {
        List<Byte> ids = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                Integer fid = nameToId.get(name);
                if (fid != null) {
                    ids.add(fid.byteValue());
                }
            }
        }
        byte[] payload = new byte[2 + ids.size()];
        payload[0] = (byte) Protocol.DISPLAY_FIELDS;
        payload[1] = (byte) ids.size();
        for (int i = 0; i < ids.size(); i++) {
            payload[2 + i] = ids.get(i);
        }

        sendFrame(Protocol.PKT_DISPLAY, payload);
    }

    @Override
    public synchronized void sendFrame(int ptype, byte[] payload) throws IOException {
        byte[] frame = Protocol.buildFrame(ptype, payload);
        if (port.isOpen()) {
            OutputStream out = port.getOutputStream();
            out.write(frame);
            out.flush();
        }
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        if (!port.isOpen()) {
            port.openPort();
        }
        running = true;
        readerThread = new Thread(this::readerLoop, "Jiro-ReaderThread");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (readerThread != null) {
            try {
                readerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readerThread = null;
        }
        try {
            port.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Map<Integer, FieldSchema> getSchema() {
        return Collections.unmodifiableMap(schema);
    }

    @Override
    public Map<String, Integer> getNameToId() {
        return Collections.unmodifiableMap(nameToId);
    }

    @Override
    public void handleConfig(byte[] payload) {
        int fid = payload[0] & 0xFF;
        int ftype = payload[1] & 0xFF;
        int role = payload[2] & 0xFF;
        int nlen = payload[3] & 0xFF;
        String name = new String(payload, 4, nlen, StandardCharsets.UTF_8);

        schema.put(fid, new FieldSchema(fid, name, ftype, role));
        nameToId.put(name, fid);
        System.out.printf("Feld erkannt: %s (id=%d, typ=%d, rolle=%d)%n", name, fid, ftype, role);
    }

    @Override
    public void handleData(byte[] payload) {
        int count = payload[0] & 0xFF;
        int offset = 1;
        Map<String, Object> out = new LinkedHashMap<>();

        for (int i = 0; i < count; i++) {
            if (offset + 2 > payload.length) {
                break;
            }
            int fid = payload[offset] & 0xFF;
            int size = payload[offset + 1] & 0xFF;
            int dataStart = offset + 2;
            offset += 2 + size;

            if (fid < 0 || !schema.containsKey(fid)) {
                continue;
            }
            FieldSchema field = schema.get(fid);
            String name = field.getName();
            Object value = Protocol.decodeValue(field.getType(), payload, dataStart);
            out.put(name, value);

            List<Consumer<Object>> cbs = callbacks.get(name);
            if (cbs != null) {
                for (Consumer<Object> cb : cbs) {
                    try {
                        cb.accept(value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (!out.isEmpty()) {
            System.out.println("Telemetrie: " + out);
        }
    }

    private void readerLoop() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            InputStream in = port.getInputStream();
            while (running) {
                int b;
                try {
                    b = in.read();
                } catch (InterruptedIOException e) {
                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    continue;
                }
                if (b == -1) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }

                if (buf.size() == 0 && (b & 0xFF) != Protocol.FRAME_START) {
                    continue; // sync on start byte
                }
                buf.write(b);

                byte[] current = buf.toByteArray();
                if (current.length >= 3) {
                    int payloadLen = current[2] & 0xFF;
                    int totalLen = 4 + payloadLen;
                    if (current.length == totalLen) {
                        int ptype = current[1] & 0xFF;
                        byte[] payload = Arrays.copyOfRange(current, 3, 3 + payloadLen);
                        int cs = current[3 + payloadLen] & 0xFF;

                        if (Protocol.checksum(current, 1, payloadLen + 2) == cs) {
                            if (ptype == Protocol.PKT_CONFIG) {
                                handleConfig(payload);
                            } else if (ptype == Protocol.PKT_DATA) {
                                handleData(payload);
                            }
                        }
                        buf.reset();
                    } else if (current.length > totalLen) {
                        buf.reset();
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }
}
