package io.github.gohoski.numai.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

public class ConnectionInputStream extends InputStream {
    private final InputStream inputStream;
    private final HttpURLConnection connection;

    public ConnectionInputStream(InputStream inputStream, HttpURLConnection connection) {
        this.inputStream = inputStream;
        this.connection = connection;
    }

    @Override
    public int read() throws IOException {
        if (inputStream == null) {
            return -1;
        }
        return inputStream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (inputStream == null) {
            return -1;
        }
        return inputStream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (inputStream == null) {
            return -1;
        }
        return inputStream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        if (inputStream == null) {
            return 0;
        }
        return inputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        if (inputStream == null) {
            return 0;
        }
        return inputStream.available();
    }

    @Override
    public void close() throws IOException {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        if (inputStream != null) {
            inputStream.mark(readlimit);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (inputStream != null) {
            inputStream.reset();
        }
    }

    @Override
    public boolean markSupported() {
        return inputStream != null && inputStream.markSupported();
    }
}