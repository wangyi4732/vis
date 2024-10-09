package com.yys.controller;

import com.alibaba.fastjson2.JSON;
import com.yys.entity.AiModels;
import com.yys.entity.JsonMsg;
import com.yys.entity.ModelPlan;
import com.yys.entity.Result;
import com.yys.service.CameralistService;
import com.yys.service.ModelPlanService;
import com.yys.service.SftpService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/plan")
@CrossOrigin
public class ModelPlanController {

    @Value("${file.upload-dir}")
    private String uploadDir;
    @Value("${minio.imgbucket.name}")
    private String bucketName;
    @Value("${linuxadress.adress}")
    private String adress;
    @Value("${linuxadress.password}")
    private String password;
    @Value("${linuxadress.username}")
    private String username;
    @Value("${linuxadress.upload_dir}")
    private String upload_dir;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private SftpService sftpService;

    @Autowired
    private CameralistService cameraService;

    @Autowired
    private ModelPlanService modelPlanService;

    @GetMapping("/getPlans")
    public String getPlans(@RequestParam(value = "scene", required = false) String scene) {
        Result rs = modelPlanService.getMainMsgList(scene);
        return JSON.toJSONString(rs);
    }

    @GetMapping("/getModelPlanbyid")
    public String getModelPlan(@RequestParam(value = "Id", required = false) Integer Id) {
        Result rs = modelPlanService.getModelPlan(Id);
        return JSON.toJSONString(rs);
    }

    @GetMapping("/getModelTypes")
    public String getModelTypes(){
        Result rs = modelPlanService.getModelTypes();
        return JSON.toJSONString(rs);
    }


    @PostMapping("/saveModelMsg")
    public String handleFileUpload(@ModelAttribute MultipartFile modelFile) throws IOException {

        if (!modelFile.getOriginalFilename().endsWith(".zip")) {
            return JSON.toJSONString(Result.success(500, "上传的文件不是zip格式，请重新上传！",1, "上传失败"));
        }
        if (modelFile.isEmpty()) {
            return JSON.toJSONString(Result.success(500, "上传的文件为空，请重新上传！",1, "上传失败"));
        }

        // 保存上传的压缩包到服务器
        Path zipFilePath = Paths.get(uploadDir, modelFile.getOriginalFilename());

        // 先检查目录是否存在，如果不存在则创建
        Files.createDirectories(zipFilePath.getParent());


        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile())) {
            fos.write(modelFile.getBytes());
        } catch (IOException e) {
            throw new IOException("保存上传文件失败：" + e.getMessage(), e);
        }
        // 解压文件
        unzipFile(zipFilePath.toFile(), uploadDir);

        // 清理已处理的文件
        cleanupProcessedFiles(zipFilePath);

        return JSON.toJSONString(Result.success(200,"上传成功",1, "上传成功"));
    }

    private void unzipFile(File zipFile, String outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry zipEntry;
            Integer aimodelid=null;
            JsonMsg jsonMsg=null;
            List<String> modelTypes = new ArrayList<>();
            while ((zipEntry = zis.getNextEntry()) != null) {
                String fileName = zipEntry.getName();
                Path newPath = Paths.get(outputDir, fileName);

                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);

                    // 根据文件类型处理
                    if (fileName.endsWith(".pt")) {
                        aimodelid=processModelFile(newPath.toFile());
                    } else if (fileName.endsWith(".json")) {
                        jsonMsg=processJsonFile(newPath.toFile());
                    } else if (isImageFile(fileName)) {
                        modelTypes.add(processImageFile(newPath.toFile()));
                    }
                }
            }
            if(aimodelid!=null){
                //更新模型名称
                cameraService.updateModelname(aimodelid,jsonMsg.getModelName());
                //添加模型商城
                ModelPlan modelPlan = new ModelPlan();
                modelPlan.setModelId(String.valueOf(aimodelid));
                modelPlan.setModelName(jsonMsg.getModelName());
                modelPlan.setModelExplain(jsonMsg.getModelExplain());
                modelPlan.setModelType(jsonMsg.getModelTypes());
                for (String modelType : modelTypes) {
                    if (getminiomsg(modelType ).equals("img")){
                        modelPlan.setImgs("/"+modelType);
                    }else if (getminiomsg(modelType ).equals("test")){
                        modelPlan.setImgTest("/"+modelType);
                    }else if (getminiomsg(modelType ).equals("detail")) {
                        modelPlan.setImgDetail("/"+modelType);
                    }
                }
                //添加标签
                StringBuilder sb = new StringBuilder();
                List<String> list = jsonMsg.getModelTypes();
                for (String modelType : list) {
                    Integer modelTypeId =modelPlanService.getmodeltypeid(modelType);
                    if (modelTypeId == null){
                        modelPlanService.insterTypes(modelType);
                    }
                    sb.append(modelType).append(",");

                }
                // 删除最后一个逗号
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
                modelPlan.setScene(sb.toString());
                modelPlanService.insertModelPlan(modelPlan);

            }
        } catch (IOException e) {
            throw new IOException("解压文件失败：" + e.getMessage(), e);
        }
    }

    private boolean isImageFile(String fileName) {
        String[] imageExtensions = {"jpg", "jpeg", "png", "gif"};

        for (String ext : imageExtensions) {
            if (fileName.toLowerCase().endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private int processModelFile(File file) {
        // 将.pt文件移动到指定的服务器目录
        Path modelDir = Paths.get(uploadDir, "models");
        try {
            if (!Files.exists(modelDir)) {
                Files.createDirectories(modelDir);
            }

            // 避免文件覆盖
            String fileName = file.getName();
            Path targetPath = modelDir.resolve(fileName);
            int i = 1;
            while (Files.exists(targetPath)) {
                String newName = fileName.substring(0, fileName.lastIndexOf('.')) + "_" + i + fileName.substring(fileName.lastIndexOf('.'));
                targetPath = modelDir.resolve(newName);
                i++;
            }
            Files.move(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 上传文件
            String localFilePath = targetPath.toString();
            sftpService.uploadFile(localFilePath, upload_dir, username, adress, 22, password);

            //记录在数据库
            AiModels aiModels = new AiModels();
            aiModels.setModel(fileName);
            aiModels.setModelLocation(getUploadDir(upload_dir));
            aiModels.setModelVersion("v1.0.0");
            aiModels.setModelSource(1);
            aiModels.setModelName(fileName);
            String time=getnowtime();
            aiModels.setCreateTime(time);
            aiModels.setUpdateTime(time);

            cameraService.insterModel(aiModels);
            // 删除本地文件
            Files.deleteIfExists(targetPath);
            return aiModels.getId();
        } catch (IOException e) {
            System.err.println("Error processing the model file: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    private JsonMsg processJsonFile(File file) throws IOException {
        // 读取JSON文件内容并打印
        String content = new String(Files.readAllBytes(file.toPath()));
        JsonMsg jsonMsg = JSON.parseObject(content, JsonMsg.class);
        return jsonMsg;
    }

    private String processImageFile(File file) throws IOException {
        // 将图片上传到MinIO
        String[] parts = file.getName().split("_");
        String folderName = parts[0];

        // 设置存储路径
        String objectName = folderName + "/" + file.getName();
        try (FileInputStream fis = new FileInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(fis, file.length(), -1)
                            .contentType("image/jpeg")
                            .build()
            );
            return bucketName + "/" +objectName;
        } catch (Exception e) {
            throw new IOException("上传到 MinIO 失败：" + e.getMessage(), e);
        }
    }

    private void cleanupProcessedFiles(Path zipFilePath) throws IOException {
        // 清理已处理的文件
        Files.walk(zipFilePath.getParent())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private String getnowtime(){
        LocalDateTime now = LocalDateTime.now();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SS");

        String formattedDateTime = now.format(formatter);
        return formattedDateTime;
    }

    private String getUploadDir(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    private String getminiomsg(String path) {
        String[] parts = path.split("/");
        return parts[1];
    }

}
