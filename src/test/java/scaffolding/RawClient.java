package scaffolding;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RawClient implements Closeable {
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private BufferedOutputStream request;
    private Socket socket;
    private InputStream response;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicReference<Exception> exception = new AtomicReference<>();

    public static RawClient create(URI uri) throws IOException {
        RawClient rawClient = new RawClient();
        rawClient.start(uri);
        return rawClient;
    }

    private void start(URI uri) throws IOException {
        this.socket = new Socket(uri.getHost(), uri.getPort());
        this.request = new BufferedOutputStream(socket.getOutputStream(), 2048);
        this.response = socket.getInputStream();
        isConnected.set(true);

        executorService.submit(() -> {
            byte[] buffer = new byte[8192];
            int read;
            try {
                while ((read = response.read(buffer)) > -1) {
//                    System.out.println("Got " + read + " bytes: " + new String(buffer, 0, read, UTF_8));
                    if (read > 0) {
                        baos.write(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
//                System.out.println("Got exception " + e);
                exception.set(e);
                isConnected.set(false);
            }

        });
    }

    public RawClient send(byte[] bytes) throws IOException {
        if (bytes.length > 0) {
            request.write(bytes);
        }
        return this;
    }
    public RawClient sendUTF8(String message) throws IOException {
        send(message.getBytes(UTF_8));
        return this;
    }
    public RawClient sendLine(String line) throws IOException {
        sendUTF8(line + "\r\n");
        return this;
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public RawClient sendStartLine(String method, String target) throws IOException {
        sendLine(method + " " + target + " HTTP/1.1");
        return this;
    }

    public RawClient sendHeader(String name, String value) throws IOException {
        sendLine(name + ": " + value);
        return this;
    }
    public RawClient endHeaders() throws IOException {
        sendLine("");
        return this;
    }
    public RawClient flushRequest() throws IOException {
        request.flush();
        return this;
    }

    public RawClient closeRequest() throws IOException {
        request.close();
        request = null;
        return this;
    }
    public RawClient closeResponse() throws IOException {
        response.close();
        response = null;
        return this;
    }

    public long bytesReceived() {
        return baos.size();
    }

    public String responseString() {
        try {
            return baos.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] asBytes() {
        return baos.toByteArray();
    }

    @Override
    public void close() {
        close(request);
        close(response);
        close(socket);
        isConnected.set(false);
    }

    private static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
            }
        }
    }

    public void clear() {
        baos.reset();
    }
}
