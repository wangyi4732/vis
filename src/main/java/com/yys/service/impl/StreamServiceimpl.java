package com.yys.service.impl;

import com.yys.service.StreamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class StreamServiceimpl implements StreamService {

    @Value("${stream.python-url}")
    private String pythonUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String startStream(String[] rtspUrls, String[] modelPaths, String taskId) {
        String url = pythonUrl + "/start_stream";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        StringBuilder rtspUrlsBuilder = new StringBuilder("[");
        for (String urls : rtspUrls) {
            if ("0".equals(urls)) {
                rtspUrlsBuilder.append(0).append(",");
            } else {
                rtspUrlsBuilder.append("\"").append(urls).append("\",");
            }
        }
        if (rtspUrls.length > 0) {
            rtspUrlsBuilder.setLength(rtspUrlsBuilder.length() - 1);
        }
        rtspUrlsBuilder.append("]");

        String modelPathsJson = toJsonArray(modelPaths);

        // 拼接 JSON 字符串，包含 taskId
        String json = "{\"rtsp_urls\":" + rtspUrlsBuilder.toString() +
                ",\"model_paths\":" + modelPathsJson +
                ",\"task_id\":\"" + taskId + "\"}";

        HttpEntity<String> request = new HttpEntity<>(json, headers);
        return restTemplate.postForObject(url, request, String.class);
    }


    @Override
    public String stopStream(String name) {
        String url = pythonUrl + "/stop_stream/";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 正确构建JSON字符串
        String json = "{\"name\":\"" + name + "\"}";
        HttpEntity<String> request = new HttpEntity<>(json, headers);
        return restTemplate.postForObject(url, request, String.class);
    }


    // 将数组转换为JSON数组字符串
    private String toJsonArray(String[] array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < array.length; i++) {
            sb.append("\"").append(array[i]).append("\"");
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
