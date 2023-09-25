package cn.kungreat.boot.services;

import cn.kungreat.boot.CpdogMain;
import cn.kungreat.boot.an.CpdogController;
import cn.kungreat.boot.filter.BaseWebSocketFilter;
import cn.kungreat.boot.handler.WebSocketConvertData;
import cn.kungreat.boot.jb.BaseResponse;
import cn.kungreat.boot.utils.JdbcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;

@CpdogController(index = 6)
public class FileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);

    public static String testFileUpload(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        try {
            baseConvertFile(job);
            if (job.isFileDone()) {
                LOGGER.info("文件保存完成{}", job.getFileName());
            }
        } catch (Exception exception) {
            LOGGER.error(exception.getMessage());
        }
        return rt;
    }

    public static String uploadUserImg(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        try {
            baseConvertFile(job);
            if (job.isFileDone()) {
                LOGGER.info("文件保存完成{}", job.getFileName());
                rt = changeUserImg(job);
            }
        } catch (Exception exception) {
            LOGGER.error(exception.getMessage());
        }
        return rt;
    }

    public static String changeUserImg(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        String nikeName = BaseWebSocketFilter.USER_UUIDS.get(job.getSrc());
        final BaseResponse baseResponse = new BaseResponse();
        try (Connection connection = JdbcUtils.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("update user_details set img_path=? where nike_name=?")) {
            preparedStatement.setString(1, "/images/user/" + job.getFileName());
            preparedStatement.setString(2, nikeName);
            int i = preparedStatement.executeUpdate();
            if (i > 0) {
                baseResponse.setCode("200");
                baseResponse.setMsg("图片上传成功");
                baseResponse.setUrl("uploadUserImg");
                baseResponse.setImgPath("/images/user/" + job.getFileName());
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
            } else {
                baseResponse.setMsg("图片上传失败");
                baseResponse.setUrl("uploadUserImg");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
            }
            connection.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rt;
    }

    private static void baseConvertFile(WebSocketConvertData.ReceiveObj job) throws Exception {
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
    }

}
