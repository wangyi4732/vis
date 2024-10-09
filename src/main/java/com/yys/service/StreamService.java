package com.yys.service;


public interface StreamService {

    // 启动视频流
    String startStream(String[] rtspUrls, String[] modelPaths,String taskId) ;

    // 停止视频流
    String stopStream(String name);


}


