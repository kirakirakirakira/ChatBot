package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 用户列表的查询条件。
 * <p>
 * 用 Criteria 而不是 JPQL 里的 {@code (:role is null or u.role = :role)}：那种写法参数为 null 时
 * 条件恒真，MySQL 优化器基本会放弃 role/status 上的索引直接全表扫；而且每个可选条件都要多绑一个参数，
 * 三个筛选器就是六个。Criteria 是「传了才拼」，用不到的条件压根不出现在 SQL 里。
 * <p>
 * 放在 repository 包而不是 service 包：它是查询定义，和 Repository 接口是一伙的；
 * service 只负责把筛选参数原样传进来，不碰 Criteria API。
 */
public final class UserSpecifications {

    /** LIKE 的转义符，同时也是需要被转义的字符之一（见 {@link #escapeLike}）。 */
    private static final char ESCAPE_CHAR = '\\';

    private UserSpecifications() {
    }

    /**
     * @param keyword 对 username 做忽略大小写的模糊匹配；null 或全空白 = 不按关键字筛。
     *                % _ \ 三个字符会被转义：否则搜「100%」变成「以 100 开头的任意串」，
     *                搜「_」匹配上所有用户名——用户输入不该被当成 SQL 通配符。
     * @param role    null = 不限角色
     * @param status  null = 不限状态
     */
    public static Specification<User> search(String keyword, Integer role, Integer status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(3);
            String trimmed = (keyword == null) ? null : keyword.strip();
            if (trimmed != null && !trimmed.isEmpty()) {
                String pattern = "%" + escapeLike(trimmed).toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("username")), pattern, ESCAPE_CHAR));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            // 一个条件都没有时返回恒真谓词而不是 null：分页的 count 查询会复用同一个 Specification，
            // 显式 conjunction 比「靠 null 表示无条件」在两个查询上的行为更好预测
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 只转义用户输入的原文，两端表示「前后模糊」的 % 由调用方拼，不参与转义。
     * toLowerCase 用 Locale.ROOT：跟着系统默认区域走的话，土耳其语环境会把 I 变成 ı，
     * 和列上的 lower() 对不上，同一个关键字在不同机器上搜出不同结果。
     */
    private static String escapeLike(String raw) {
        StringBuilder escaped = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                escaped.append(ESCAPE_CHAR);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
