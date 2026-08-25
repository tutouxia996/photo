package com.sq.admin.album.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizScanPath;
import com.sq.bus.domain.vo.AlbumDownloadProgress;
import com.sq.bus.service.IAlbumDownloadService;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizScanPathService;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 相册管理
 */
@RestController
@RequestMapping("/album/album")
public class BizAlbumController extends BaseController {

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private IBizScanPathService scanPathService;

    @Autowired
    private IAlbumDownloadService albumDownloadService;

    @PreAuthorize("@ss.hasPermi('album:album:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizAlbum query) {
        startPage();
        int deleted = query.getDeleted() == null ? AlbumDeleted.NORMAL : query.getDeleted();
        LambdaQueryWrapper<BizAlbum> wrapper = new LambdaQueryWrapper<BizAlbum>()
                .like(StringUtils.isNotEmpty(query.getAlbumName()), BizAlbum::getAlbumName, query.getAlbumName())
                .eq(query.getIsPublic() != null, BizAlbum::getIsPublic, query.getIsPublic())
                .eq(BizAlbum::getDeleted, deleted)
                .orderByAsc(BizAlbum::getSortOrder)
                .orderByDesc(BizAlbum::getAlbumId);
        List<BizAlbum> list = albumService.list(wrapper);
        return getDataTable(list);
    }

    /**
     * 启动相册后台打包（原图/原视频 → zip），进度见 /download/progress，完成后取 /download/file
     */
    @PreAuthorize("@ss.hasPermi('album:album:query') or @ss.hasPermi('album:photo:list')")
    @Log(title = "相册下载", businessType = BusinessType.EXPORT)
    @PostMapping("/{albumId}/download")
    public AjaxResult startDownload(@PathVariable Long albumId) {
        AlbumDownloadProgress p = albumDownloadService.start(albumId);
        return success(p);
    }

    /** 相册打包/下载进度（右下角浮层轮询） */
    @PreAuthorize("@ss.hasPermi('album:album:query') or @ss.hasPermi('album:photo:list') or @ss.hasPermi('album:album:list')")
    @GetMapping("/download/progress")
    public AjaxResult downloadProgress() {
        return success(albumDownloadService.getProgress());
    }

    /** 下载已打包好的 zip（文件名为相册名称） */
    @PreAuthorize("@ss.hasPermi('album:album:query') or @ss.hasPermi('album:photo:list')")
    @GetMapping("/download/file")
    public void downloadFile(@RequestParam("taskId") String taskId, HttpServletResponse response) throws IOException {
        File file = albumDownloadService.resolveReadyFile(taskId);
        if (file == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"msg\":\"打包文件未就绪或已过期\",\"code\":404}");
            return;
        }
        AlbumDownloadProgress p = albumDownloadService.getProgress();
        String zipName = p != null && StringUtils.isNotEmpty(p.getFileName()) ? p.getFileName() : file.getName();
        String asciiFallback = zipName.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(zipName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.reset();
        response.setContentType("application/zip");
        response.setHeader("Cache-Control", "no-store");
        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded);
        response.setHeader("Content-Length", String.valueOf(file.length()));
        byte[] buffer = new byte[1024 * 64];
        try (FileInputStream in = new FileInputStream(file); OutputStream out = response.getOutputStream()) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            out.flush();
        } finally {
            albumDownloadService.markConsumed(taskId);
        }
    }

    @PreAuthorize("@ss.hasPermi('album:album:query')")
    @GetMapping("/{albumId}")
    public AjaxResult getInfo(@PathVariable Long albumId) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在或已删除");
        }
        return success(album);
    }

    @PreAuthorize("@ss.hasPermi('album:album:add')")
    @Log(title = "相册管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizAlbum album) {
        album.setCreateBy(getUsername());
        album.setCreateTime(new Date());
        if (album.getIsPublic() == null) {
            album.setIsPublic(1);
        }
        if (album.getPhotoCount() == null) {
            album.setPhotoCount(0);
        }
        if (album.getSortOrder() == null) {
            album.setSortOrder(0);
        }
        album.setDeleted(AlbumDeleted.NORMAL);
        albumService.save(album);
        return success(album);
    }

    /**
     * 从服务器磁盘目录创建相册并扫描入库（不复制原图，仅索引磁盘路径）
     * body: localPath 必填；albumName 可选（默认取文件夹名）；isPublic 可选；albumDesc 可选
     */
    @PreAuthorize("@ss.hasPermi('album:album:add')")
    @Log(title = "磁盘导入相册", businessType = BusinessType.INSERT)
    @PostMapping("/importFromDisk")
    public AjaxResult importFromDisk(@RequestBody Map<String, Object> body) {
        String localPath = body == null ? null : String.valueOf(body.get("localPath"));
        if (StringUtils.isEmpty(localPath) || "null".equals(localPath)) {
            return error("请填写磁盘目录路径");
        }
        File dir = new File(localPath.trim());
        if (!dir.exists() || !dir.isDirectory()) {
            return error("本地目录不存在或不可访问：" + dir.getPath());
        }

        String albumName = body.get("albumName") == null ? null : String.valueOf(body.get("albumName")).trim();
        if (StringUtils.isEmpty(albumName) || "null".equals(albumName)) {
            albumName = dir.getName();
        }
        if (StringUtils.isEmpty(albumName)) {
            return error("无法从路径解析相册名称，请手动填写");
        }

        Integer isPublic = 1;
        if (body.get("isPublic") != null && StringUtils.isNotEmpty(String.valueOf(body.get("isPublic")))) {
            try {
                isPublic = Integer.valueOf(String.valueOf(body.get("isPublic")));
            } catch (NumberFormatException ignored) {
                isPublic = 1;
            }
        }
        String albumDesc = body.get("albumDesc") == null ? null : String.valueOf(body.get("albumDesc"));
        if ("null".equals(albumDesc)) {
            albumDesc = null;
        }

        BizAlbum album = new BizAlbum();
        album.setAlbumName(albumName);
        album.setAlbumDesc(albumDesc);
        album.setIsPublic(isPublic);
        album.setPhotoCount(0);
        album.setSortOrder(0);
        album.setDeleted(AlbumDeleted.NORMAL);
        album.setCreateBy(getUsername());
        album.setCreateTime(new Date());
        albumService.save(album);

        BizScanPath scanPath = new BizScanPath();
        scanPath.setPathName(albumName);
        scanPath.setLocalPath(dir.getAbsolutePath());
        scanPath.setDefaultAlbumId(album.getAlbumId());
        scanPath.setStatus(1);
        scanPath.setDeleted(0);
        scanPath.setCreateBy(getUsername());
        scanPath.setCreateTime(new Date());
        scanPathService.save(scanPath);

        // 异步扫描，前端按 scanLogId 轮询进度
        Long logId = scanPathService.startScanAsync(scanPath.getPathId(), true);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("album", album);
        data.put("scanPathId", scanPath.getPathId());
        data.put("scanLogId", logId);
        data.put("async", true);
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('album:album:edit')")
    @Log(title = "相册管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizAlbum album) {
        album.setUpdateBy(getUsername());
        album.setUpdateTime(new Date());
        return toAjax(albumService.updateById(album));
    }

    @PreAuthorize("@ss.hasPermi('album:album:remove')")
    @Log(title = "相册管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{albumIds}")
    public AjaxResult remove(@PathVariable Long[] albumIds) {
        return toAjax(albumService.trashAlbums(Arrays.asList(albumIds)));
    }

    @PreAuthorize("@ss.hasPermi('album:album:edit')")
    @Log(title = "相册恢复", businessType = BusinessType.UPDATE)
    @PutMapping("/restore/{albumIds}")
    public AjaxResult restore(@PathVariable Long[] albumIds) {
        return toAjax(albumService.restoreAlbums(Arrays.asList(albumIds)));
    }

    @PreAuthorize("@ss.hasPermi('album:album:remove')")
    @Log(title = "相册彻底删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/purge/{albumIds}")
    public AjaxResult purge(@PathVariable Long[] albumIds) {
        return toAjax(albumService.purgeAlbums(Arrays.asList(albumIds)));
    }

    @PreAuthorize("@ss.hasPermi('album:album:edit')")
    @Log(title = "相册管理", businessType = BusinessType.UPDATE)
    @PutMapping("/refreshStats/{albumId}")
    public AjaxResult refreshStats(@PathVariable Long albumId) {
        albumService.refreshAlbumStats(albumId);
        return success();
    }
}
