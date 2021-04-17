package roj.net.tcp.serv.response;

import roj.text.CharList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class FileResponse extends StreamResponse {
    private static final File ROOT = new File("root");

    protected final File file;

    public FileResponse(File absolute) {
        file = absolute;
    }

    public FileResponse(URI relative) {
        file = new File(ROOT,
                relative.getPath()
                        .replace('/',
                                File.separatorChar));
    }

    @Override
    public void writeHeader(CharList list) {
        String type;
        String name = file.getName();
        if (name.endsWith(".html"))
            type = "text/html; charset=UTF-8";
        else
            type = "application/octet-stream";

        list.append("Content-Type: ").append(type).append(CRLF)
                .append("Content-Length: ").append(Long.toString(length)).append(CRLF);
    }

    @Override
    protected InputStream getStream() throws IOException {
        FileInputStream stream = new FileInputStream(file);
        length = stream.available();
        return stream;
    }
}
