package com.sq.admin.album.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轨迹管理
 */
@RestController
@RequestMapping("/album/track")
public class BizTrackController extends BaseController {

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @PreAuthorize("@ss.hasPermi('album:track:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizTrack query) {
        startPage();
        LambdaQueryWrapper<BizTrack> wrapper = new LambdaQueryWrapper<BizTrack>()
                .eq(query.getAlbumId() != null, BizTrack::getAlbumId, query.getAlbumId())
                .like(StringUtils.isNotEmpty(query.getTrackName()), BizTrack::getTrackName, query.getTrackName())
                .eq(query.getIsPublic() != null, BizTrack::getIsPublic, query.getIsPublic())
                .orderByDesc(BizTrack::getTrackId);
        return getDataTable(trackService.list(wrapper));
    }

    @PreAuthorize("@ss.hasPermi('album:track:query')")
    @GetMapping("/{trackId}")
    public AjaxResult getInfo(@PathVariable Long trackId) {
        BizTrack track = trackService.getById(trackId);
        List<BizTrackPoint> points = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence));
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("track", track);
        data.put("points", points);
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('album:track:generate')")
    @Log(title = "轨迹生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestParam Long albumId,
                               @RequestParam(required = false) String trackName,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        BizTrack track = trackService.generateTrack(albumId, trackName, startTime, endTime);
        track.setCreateBy(getUsername());
        trackService.updateById(track);
        return success(track);
    }

    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @Log(title = "轨迹管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizTrack track) {
        track.setUpdateBy(getUsername());
        track.setUpdateTime(new Date());
        return toAjax(trackService.updateById(track));
    }

    @PreAuthorize("@ss.hasPermi('album:track:remove')")
    @Log(title = "轨迹管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{trackIds}")
    public AjaxResult remove(@PathVariable Long[] trackIds) {
        for (Long trackId : trackIds) {
            trackPointService.remove(new LambdaQueryWrapper<BizTrackPoint>().eq(BizTrackPoint::getTrackId, trackId));
        }
        return toAjax(trackService.removeByIds(Arrays.asList(trackIds)));
    }
}
