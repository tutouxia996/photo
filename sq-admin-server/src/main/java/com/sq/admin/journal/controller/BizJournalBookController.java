package com.sq.admin.journal.controller;

import com.sq.admin.journal.support.JournalAccessHelper;
import com.sq.bus.domain.BizJournalBook;
import com.sq.bus.service.IBizJournalBookService;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日记本
 */
@RestController
@RequestMapping("/journal/book")
public class BizJournalBookController extends BaseController {

    @Autowired
    private IBizJournalBookService bookService;

    @Autowired
    private JournalAccessHelper accessHelper;

    /**
     * 获取或创建当前用户默认日记本（登录即可）。
     */
    @GetMapping("/mine")
    public AjaxResult mine() {
        String username = accessHelper.currentUsername();
        if (StringUtils.isEmpty(username)) {
            return error("未登录");
        }
        return success(bookService.getOrCreateMine(username));
    }

    @PreAuthorize("@ss.hasPermi('journal:book:query')")
    @GetMapping("/{bookId}")
    public AjaxResult getInfo(@PathVariable Long bookId) {
        BizJournalBook book = bookService.getById(bookId);
        AjaxResult deny = accessHelper.denyIfCannotEdit(book);
        if (deny != null) {
            return deny;
        }
        return success(book);
    }
}
