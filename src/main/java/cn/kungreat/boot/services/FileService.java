package cn.kungreat.boot.services;

import cn.kungreat.boot.CpdogMain;
import cn.kungreat.boot.an.CpdogController;
import cn.kungreat.boot.handler.WebSocketConvertData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@CpdogController(index = 6)
public class FileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);

    public static String testFileUpload(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        try {
            Path filePath = Path.of(CpdogMain.FILE_PATH, job.getFileName());
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
            ByteBuffer fileBuffer = job.getFileReceiveConvert();
            fileBuffer.flip();
            if (fileBuffer.hasRemaining()) {
                byte[] bts = new byte[fileBuffer.remaining()];
                System.arraycopy(fileBuffer.array(), 0, bts, 0, fileBuffer.remaining());
                Files.write(filePath, bts, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            }
            fileBuffer.clear();
            if (job.isFileDone()) {
                LOGGER.info("文件保存完成{}", job.getFileName());
            }
        } catch (Exception exception) {
            LOGGER.error(exception.getMessage());
        }
        return rt;
    }

}
