package com.sq.admin.journal.controller;

import com.sq.admin.journal.support.JournalAccessHelper;
import com.sq.bus.domain.BizJournalBook;
import com.sq.bus.domain.BizJournalPage;
import com.sq.bus.service.IBizJournalBookService;
import com.sq.bus.service.IBizJournalPageService;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日记页
 */
@RestController
@RequestMapping("/journal/page")
public class BizJournalPageController extends BaseController {

    @Autowired
    private IBizJournalPageService pageService;

    @Autowired
    private IBizJournalBookService bookService;

    @Autowired
    private JournalAccessHelper accessHelper;

    /**
     * 按页码范围批量拉取（无记录的页前端视为空 layers）。
     */
    @GetMapping("/range")
    public AjaxResult range(@RequestParam Long bookId,
                            @RequestParam Integer from,
                            @RequestParam Integer to) {
        BizJournalBook book = bookService.getById(bookId);
        AjaxResult deny = accessHelper.denyIfCannotEdit(book);
        if (deny != null) {
            return deny;
        }
        int pageCount = book.getPageCount() == null ? 1000 : book.getPageCount();
        int f = Math.max(1, from == null ? 1 : from);
        int t = Math.min(pageCount, to == null ? f : to);
        if (t < f) {
            return error("页码范围无效");
        }
        List<BizJournalPage> list = pageService.listByRange(bookId, f, t);
        Map<String, Object> data = new HashMap<>(4);
        data.put("bookId", bookId);
        data.put("from", f);
        data.put("to", t);
        data.put("pages", list);
        return success(data);
    }

    /**
     * 按 bookId + pageNo upsert content_json。
     */
    @Log(title = "日记页", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult upsert(@RequestBody BizJournalPage body) {
        if (body == null || body.getBookId() == null || body.getPageNo() == null) {
            return error("bookId 与 pageNo 不能为空");
        }
        BizJournalBook book = bookService.getById(body.getBookId());
        AjaxResult deny = accessHelper.denyIfCannotEdit(book);
        if (deny != null) {
            return deny;
        }
        int pageCount = book.getPageCount() == null ? 1000 : book.getPageCount();
        if (body.getPageNo() < 1 || body.getPageNo() > pageCount) {
            return error("页码超出范围");
        }
        String username = accessHelper.currentUsername();
        if (StringUtils.isEmpty(username)) {
            return error("未登录");
        }
        String content = body.getContentJson();
        if (content == null) {
            content = "{\"v\":1,\"layers\":[]}";
        }
        BizJournalPage saved = pageService.upsertContent(
                body.getBookId(), body.getPageNo(), content, username);
        return success(saved);
    }
}
