package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackGpxFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IBizTrackGpxFileService extends IService<BizTrackGpxFile> {

    List<BizTrackGpxFile> listByAlbum(Long albumId);

    /**
     * 导入 GPX 文件（仅存文件，不新建轨迹）。
     * 返回中 track 为相册照片主轨迹（若有），gpxOverlays 为可叠加折线。
     */
    Map<String, Object> importGpxFiles(Long albumId, MultipartFile[] files, String username);

    boolean setEnabled(Long gpxId, boolean enabled, String username);

    /**
     * 手动设置 GPX 出行方式（影响地图线路颜色与图例筛选）。
     */
    boolean setTravelMode(Long gpxId, String travelMode, String username);

    boolean removeGpx(Long gpxId, String username);

    /**
     * 软删相册下全部未删 GPX 库记录（不删磁盘文件）。
     * 用于相册已无轨迹时的级联清理，避免重新生成轨迹后仍叠加历史 GPX。
     */
    int removeByAlbum(Long albumId, String username);

    /**
     * 清理历史独立 GPX 轨迹并补回照片轨（兼容旧调用名）。
     */
    BizTrack rebuildAlbumTrack(Long albumId);

    /**
     * 相册启用中的 GPX 叠层折线（GCJ-02），
     * 并附带拍摄时间匹配到的媒体 matchedPhotos（挂在 GPX 坐标上）。
     */
    List<Map<String, Object>> listOverlays(Long albumId);

    /**
     * 按轨迹的 gpxEnabled 决定是否返回 GPX 折线；
     * 关闭时仍返回 matchedPhotos（不返回 path），地图只隐藏线路、保留照片。
     */
    List<Map<String, Object>> listOverlaysForTrack(BizTrack track);

    /**
     * 列表/详情回填：hasGpx；按「启用轨迹 / 启用GPX」合并展示点位/里程/时长（关闭轨迹不计照片轨）。
     */
    void enrichTracks(List<BizTrack> tracks);
}
