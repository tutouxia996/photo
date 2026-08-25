package com.sq.bus.config;

import com.sq.bus.utils.ExternalMediaTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 启动时根据 album 配置定位 ffmpeg / ffprobe。
 */
@Component
public class AlbumMediaConfig {

    @Autowired
    private AlbumProperties albumProperties;

    @PostConstruct
    public void initMediaTools() {
        ExternalMediaTools.configure(albumProperties.getFfmpegPath(), albumProperties.getFfprobePath());
    }
}
