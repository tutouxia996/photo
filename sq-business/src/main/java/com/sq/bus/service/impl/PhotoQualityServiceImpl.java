package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.vo.PhotoScoreProgress;
import com.sq.bus.domain.vo.PhotoScoreRequest;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IPhotoQualityService;
import com.sq.bus.service.score.PhotoQualityAnalyzer;
import com.sq.bus.service.score.PhotoQualityResult;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PhotoQualityServiceImpl implements IPhotoQualityService {

    private static final Logger log = LoggerFactory.getLogger(PhotoQualityServiceImpl.class);

    private static final int FLUSH_EVERY = 12;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumProperties albumProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger status = new AtomicInteger(1);
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger done = new AtomicInteger();
    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private volatile Long albumId;
    private volatile String albumName = "";
    private volatile String currentFile = "";
    private volatile String message = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "album-photo-score");
            t.setDaemon(true);
            return t;
        }
    });

    @Override
    public Map<String, Object> startScoreAlbum(Long albumId, PhotoScoreRequest request) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            throw new ServiceException("相册不存在");
        }
        if (!running.compareAndSet(false, true)) {
            throw new ServiceException("已有打分任务进行中（" + safeName(this.albumName) + "），请稍后再试");
        }

        this.albumId = albumId;
        this.albumName = album.getAlbumName() == null ? "" : album.getAlbumName();
        status.set(0);
        total.set(0);
        done.set(0);
        passed.set(0);
        failed.set(0);
        skipped.set(0);
        currentFile = "";
        message = "正在准备打分…";

        final PhotoScoreRequest req = request;
        final Long targetAlbumId = albumId;
        final String targetAlbumName = this.albumName;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runScore(targetAlbumId, targetAlbumName, req);
                } catch (Exception e) {
                    log.warn("photo score job failed albumId={}: {}", targetAlbumId, e.getMessage());
                    status.set(2);
                    message = "打分失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                } finally {
                    running.set(false);
                    currentFile = "";
                }
            }
        });

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("started", true);
        map.put("progress", snapshot());
        return map;
    }

    @Override
    public PhotoScoreProgress getScoreProgress() {
        return snapshot();
    }

    private void runScore(Long albumId, String albumName, PhotoScoreRequest request) {
        AlbumProperties.PhotoScoreConfig cfg = albumProperties.getPhotoScore();
        int passScore = cfg == null ? 70 : cfg.getPassScore();
        int analyzeWidth = cfg == null ? 320 : cfg.getAnalyzeWidth();
        int minShortSide = cfg == null ? 720 : cfg.getMinShortSide();
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());

        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .orderByAsc(BizPhoto::getPhotoId);
        List<BizPhoto> all = photoService.list(wrapper);
        Set<Long> idFilter = null;
        if (request != null && request.getPhotoIds() != null && !request.getPhotoIds().isEmpty()) {
            idFilter = new HashSet<Long>(request.getPhotoIds());
        }

        List<BizPhoto> targets = new ArrayList<BizPhoto>();
        int skipCount = 0;
        for (BizPhoto photo : all) {
            if (idFilter != null && !idFilter.contains(photo.getPhotoId())) {
                continue;
            }
            if (!force && photo.getAestheticScore() != null) {
                skipCount++;
                continue;
            }
            targets.add(photo);
        }
        skipped.set(skipCount);
        total.set(targets.size());
        done.set(0);
        passed.set(0);
        failed.set(0);
        status.set(0);
        message = targets.isEmpty()
                ? "没有需要打分的照片"
                : ("开始打分，共 " + targets.size() + " 张");

        if (targets.isEmpty()) {
            status.set(1);
            message = "已跳过 " + skipCount + " 张，无需打分";
            return;
        }

        Date now = new Date();
        List<BizPhoto> buffer = new ArrayList<BizPhoto>();
        for (int i = 0; i < targets.size(); i++) {
            BizPhoto photo = targets.get(i);
            currentFile = photo.getFileName() == null ? ("#" + photo.getPhotoId()) : photo.getFileName();
            message = "正在打分 " + (i + 1) + "/" + targets.size() + "：" + currentFile;

            PhotoQualityResult result;
            if (photo.getFileType() != null && photo.getFileType() == 2) {
                result = PhotoQualityResult.fail(0, "视频不参与图生图");
            } else {
                File file = resolveFile(photo);
                result = PhotoQualityAnalyzer.analyze(file, analyzeWidth, minShortSide, passScore);
            }
            photo.setAestheticScore(result.getScore());
            photo.setScorePass(result.isPassed() ? 1 : 0);
            photo.setScoreReason(result.getReason());
            photo.setScoredAt(now);
            buffer.add(photo);

            done.incrementAndGet();
            if (result.isPassed()) {
                passed.incrementAndGet();
            } else {
                failed.incrementAndGet();
            }

            if (buffer.size() >= FLUSH_EVERY || i == targets.size() - 1) {
                photoService.updateBatchById(buffer, 80);
                buffer.clear();
            }
        }

        status.set(1);
        message = "完成：合格 " + passed.get() + "，不合格 " + failed.get()
                + (skipCount > 0 ? "，跳过 " + skipCount : "");
        log.info("photo score albumId={} name={} done={} passed={} failed={} skipped={}",
                albumId, albumName, done.get(), passed.get(), failed.get(), skipCount);
    }

    private PhotoScoreProgress snapshot() {
        PhotoScoreProgress p = new PhotoScoreProgress();
        int t = total.get();
        int d = done.get();
        p.setStatus(status.get());
        p.setAlbumId(albumId);
        p.setAlbumName(albumName);
        p.setTotal(t);
        p.setDone(d);
        p.setPassed(passed.get());
        p.setFailed(failed.get());
        p.setSkipped(skipped.get());
        p.setRemaining(Math.max(0, t - d));
        p.setPercent(t <= 0 ? (status.get() == 1 ? 100 : 0) : Math.min(100, (int) Math.round(d * 100.0 / t)));
        p.setRunning(running.get() || status.get() == 0);
        p.setCurrentFile(currentFile == null ? "" : currentFile);
        p.setMessage(message == null ? "" : message);
        return p;
    }

    @Override
    public boolean isEligibleForDraw(BizPhoto photo) {
        if (photo == null) {
            return false;
        }
        if (photo.getDeleted() != null && photo.getDeleted() != AlbumDeleted.NORMAL) {
            return false;
        }
        if (photo.getFileType() != null && photo.getFileType() == 2) {
            return false;
        }
        if ("ai_draw".equals(photo.getOriginType())) {
            return false;
        }
        return photo.getScorePass() != null && photo.getScorePass() == 1;
    }

    private File resolveFile(BizPhoto photo) {
        if (photo == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(photo.getFilePath())) {
            File origin = new File(photo.getFilePath());
            if (origin.exists() && origin.isFile()) {
                return origin;
            }
        }
        return null;
    }

    private static String safeName(String name) {
        return StringUtils.isEmpty(name) ? "其他相册" : name.trim();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
