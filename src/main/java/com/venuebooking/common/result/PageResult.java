package com.venuebooking.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应。为什么不直接把 MyBatis-Plus 的 Page 对象序列化给前端：
 * Page 里有十几层插桩字段（orders/optimizeCountSql...），既冗余又把
 * ORM 实现细节泄漏给了前端 —— 换 ORM 前端就得改。收口成自己的结构。
 */
@Getter
@AllArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private long total;
    private long pageNum;
    private long pageSize;
    private long pages;

    /** entity -> VO 转换收在 of() 里，调用方不再手写 stream 映射 */
    public static <E, R> PageResult<R> of(Page<E> page, Function<E, R> converter) {
        List<R> list = page.getRecords().stream().map(converter).toList();
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }
}
