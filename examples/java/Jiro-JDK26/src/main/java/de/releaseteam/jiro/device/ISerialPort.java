package de.releaseteam.jiro.device;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface ISerialPort extends Closeable {

    void openPort() throws IOException;

    InputStream getInputStream();

    OutputStream getOutputStream();

    boolean isOpen();

    void close() throws IOException;
}
