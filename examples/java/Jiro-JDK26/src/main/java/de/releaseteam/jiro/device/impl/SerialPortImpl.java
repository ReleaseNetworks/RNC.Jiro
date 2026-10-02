package de.releaseteam.jiro.device.impl;

import com.fazecast.jSerialComm.SerialPort;
import de.releaseteam.jiro.device.ISerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SerialPortImpl implements ISerialPort {

    private final SerialPort serialPort;

    public SerialPortImpl(String portName, int baudRate) {
        this.serialPort = SerialPort.getCommPort(portName);
        this.serialPort.setBaudRate(baudRate);
        this.serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 3000, 0);
    }

    public SerialPortImpl(SerialPort serialPort) {
        this.serialPort = serialPort;
    }

    @Override
    public void openPort() throws IOException {
        if (!serialPort.isOpen()) {
            if (!serialPort.openPort()) {
                throw new IOException("Failed port: " + serialPort.getSystemPortName());
            }
        }
    }

    @Override
    public InputStream getInputStream() {
        return serialPort.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        return serialPort.getOutputStream();
    }

    @Override
    public boolean isOpen() {
        return serialPort.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (serialPort.isOpen()) {
            serialPort.closePort();
        }
    }

    public SerialPort getSerialPort() {
        return serialPort;
    }

}
