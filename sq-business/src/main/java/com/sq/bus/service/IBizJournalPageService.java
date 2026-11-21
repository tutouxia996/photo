package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizJournalPage;

import java.util.List;

public interface IBizJournalPageService extends IService<BizJournalPage> {

    /**
     * 按页码范围查询（含 from、to，闭区间）。
     */
    List<BizJournalPage> listByRange(Long bookId, int from, int to);

    /**
     * 按 bookId + pageNo upsert content_json。
     */
    BizJournalPage upsertContent(Long bookId, Integer pageNo, String contentJson, String username);
}
