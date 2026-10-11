package com.venuebooking.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 游标分页（Cursor-based）结果。
 *
 * 为什么有了 pageNum 还要游标分页：
 * LIMIT offset,size 在 offset 很大时（如第 1 万页），MySQL 要先扫过并丢弃
 * offset+size 行——页码越深越慢，且随时有新数据插入导致"页码漂移"（重复/漏读）。
 * 游标分页用"上一页最后一条的 id"作为定位锚点：
 *   WHERE id < lastId ORDER BY id DESC LIMIT size
 * B+ 树直接定位，性能恒定，且天然不漂移——代价是只能顺序翻页（没有跳页）。
 * 订单流、朋友圈 feed 这类"连续往下刷"的场景，业界一律用游标分页。
 */
@Getter
@AllArgsConstructor
public class CursorPageResult<T> {

    private List<T> list;

    /** 下一页游标：本页最后一条的 id，null 表示没有更多数据 */
    private Long nextCursor;

    private boolean hasMore;
}
