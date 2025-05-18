package com.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.base-access-url}")
    private String baseAccessUrl;

    @PostConstruct
    public void init() {
        File dir = new File(uploadDir);
        
        // 确保目录存在
        if (!dir.exists()) {
            if(dir.mkdirs()) {
                logger.info("创建文件上传目录成功: {}", dir.getAbsolutePath());
            } else {
                logger.error("创建文件上传目录失败: {}", dir.getAbsolutePath());
            }
        }
        
        // 记录绝对路径方便调试
        logger.info("文件上传目录路径: {}", dir.getAbsolutePath());
        logger.info("文件访问URL前缀: {}", baseAccessUrl);
    }

    @PostMapping("/upload")
    public ResponseEntity<JSONObject> uploadFile(@RequestParam("file") MultipartFile multipartFile) {
        if (multipartFile.isEmpty()) {
            JSONObject error = new JSONObject();
            error.put("error", "上传的文件不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        try {
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String originalFilename = multipartFile.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String storedFilename = UUID.randomUUID().toString() + ext;
            Path filePath = Paths.get(uploadDir, storedFilename);
            Files.copy(multipartFile.getInputStream(), filePath);

            JSONObject response = new JSONObject();
            // 使用下载URL而不是静态资源URL
            response.put("fileUrl", "/api/files/download/" + storedFilename);
            response.put("fileName", originalFilename);
            response.put("fileType", multipartFile.getContentType());
            response.put("fileSize", multipartFile.getSize());
            
            // 根据文件类型确定消息类型
            String messageType = "FILE";
            if (multipartFile.getContentType() != null) {
                if (multipartFile.getContentType().startsWith("image/")) {
                    messageType = "IMAGE";
                } else if (multipartFile.getContentType().startsWith("video/")) {
                    messageType = "VIDEO";
                } else if (multipartFile.getContentType().startsWith("audio/")) {
                    messageType = "AUDIO";
                }
            }
            response.put("messageType", messageType);
            
            logger.info("文件上传成功: {}，大小: {} 字节", originalFilename, multipartFile.getSize());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("文件上传失败", e);
            JSONObject error = new JSONObject();
            error.put("error", "文件上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/download/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists()) {
                // 尝试确定文件的内容类型
                String contentType = null;
                try {
                    contentType = Files.probeContentType(filePath);
                } catch (IOException e) {
                    logger.error("无法确定文件类型", e);
                }
                
                // 如果无法确定内容类型，设置为二进制流
                if (contentType == null) {
                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }
                
                // 从原始文件名中提取文件扩展名
                String originalFilename = filePath.getFileName().toString();
                if (originalFilename.contains(".")) {
                    String ext = originalFilename.substring(originalFilename.lastIndexOf("."));
                    if (!originalFilename.equals(filename) && !originalFilename.startsWith(UUID.randomUUID().toString().substring(0, 8))) {
                        // 如果文件名是UUID，则使用原始扩展名生成一个更友好的文件名
                        originalFilename = "下载文件" + ext;
                    }
                }
                
                logger.info("下载文件: {}, 大小: {} 字节", originalFilename, resource.contentLength());
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFilename + "\"")
                        .body(resource);
            } else {
                logger.warn("文件不存在: {}", filename);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            logger.error("文件URL格式错误", e);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            logger.error("读取文件失败", e);
            return ResponseEntity.status(500).build();
        }
    }
} 