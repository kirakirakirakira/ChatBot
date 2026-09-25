package com.chatbot.chatbot.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 通用分页响应壳。管理端列表（用户，以及以后可能加的同级模块列表）都用它，前端只认一种分页形状。
 * <p>
 * 和 {@link MessagePageVO} 刻意不同：那边是聊天历史，界面只需要「还能不能往前翻」，
 * 而算 total 要在 LONGTEXT 大表上每翻一页多跑一次 count(*)，不值；这边是管理列表，
 * 行数有限且界面要显示「共 N 条 / 第几页 / 能不能跳页」，所以 total 和 totalPages 都带上。
 *
 * @param items      本页数据，顺序由调用方给的 Pageable 决定
 * @param page       当前页码，从 0 起，和请求参数同口径（前端不用再换算一遍）
 * @param size       每页条数，回显实际生效的值
 * @param total      满足筛选条件的总条数
 * @param totalPages 总页数；total 为 0 时是 0，不是 1
 */
public record PageVO<T>(List<T> items, int page, int size, long total, int totalPages) {

    /**
     * 把 Spring Data 的 {@link Page} 换成出网形状，并用 mapper 把 entity 转成 VO。
     * 转 VO 这一步不是可选项：entity 不出控制器是项目铁律，直接把 Page&lt;User&gt; 返回
     * 会把 password 哈希一起序列化出去。
     */
    public static <E, T> PageVO<T> of(Page<E> source, Function<E, T> mapper) {
        return new PageVO<>(source.getContent().stream().map(mapper).toList(),
                source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
