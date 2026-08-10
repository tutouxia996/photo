package com.sq.bus.service.route;

import lombok.Data;

/**
 * 火车经停站（规划用）
 */
@Data
public class TrainStop {

    /** 站名，如 清河 / 清河站 */
    private String name;

    /** 到达时刻 HH:mm，始发可为空 */
    private String arriveTime;

    /** 发车时刻 HH:mm，终点可为空 */
    private String departTime;

    /** WGS84 纬度（解析后填充） */
    private Double latWgs;

    /** WGS84 经度 */
    private Double lngWgs;

    /** GCJ-02 纬度（展示/存折线端点用） */
    private Double latGcj;

    /** GCJ-02 经度 */
    private Double lngGcj;

    /** 站序（从 1 起，可选） */
    private Integer sequence;
}
