package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.dto.BlogPostSummaryView;
import com.chatbot.chatbot.entity.BlogPost;
import com.chatbot.chatbot.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 博客文章仓库。
 * <p>
 * 列表查询一律走投影（见 {@link BlogPostSummaryView}）：不取 content、join 作者，
 * 一条 SQL 拿全列表需要的东西，不会触发 N+1。
 */
public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    /**
     * 已发布文章分页。排序写在 JPQL 里而不是 Pageable 里：投影查询的 countQuery 是单独推导的，
     * 把排序留在查询内更不容易出现「count 带 order by」这种多余 SQL。
     */
    @Query(value = """
            select p.id as id, p.title as title, p.summary as summary,
                   a.id as authorId,
                   case when a.nickname is null or a.nickname = '' then a.username else a.nickname end as authorName,
                   p.status as status, p.publishedAt as publishedAt,
                   p.createdAt as createdAt, p.updatedAt as updatedAt
            from BlogPost p
            join p.author a
            where p.status = :status
            order by p.publishedAt desc, p.id desc
            """,
            countQuery = "select count(p) from BlogPost p where p.status = :status")
    Page<BlogPostSummaryView> findPageByStatus(@Param("status") PostStatus status, Pageable pageable);

    /** 「我的文章」：所有状态都能看到，按创建时间倒序；作者 id 来自 token，不接受前端传。 */
    @Query(value = """
            select p.id as id, p.title as title, p.summary as summary,
                   a.id as authorId,
                   case when a.nickname is null or a.nickname = '' then a.username else a.nickname end as authorName,
                   p.status as status, p.publishedAt as publishedAt,
                   p.createdAt as createdAt, p.updatedAt as updatedAt
            from BlogPost p
            join p.author a
            where a.id = :authorId
            order by p.createdAt desc, p.id desc
            """,
            countQuery = "select count(p) from BlogPost p where p.author.id = :authorId")
    Page<BlogPostSummaryView> findPageByAuthorId(@Param("authorId") Long authorId, Pageable pageable);

    /**
     * 工具调用用的站内搜索：只在标题 / 摘要里做 LIKE，不扫 LONGTEXT 正文。
     * 只返回已发布文章，避免 agent 看到别人的草稿。
     */
    @Query("""
            select p.id as id, p.title as title, p.summary as summary,
                   a.id as authorId,
                   case when a.nickname is null or a.nickname = '' then a.username else a.nickname end as authorName,
                   p.status as status, p.publishedAt as publishedAt,
                   p.createdAt as createdAt, p.updatedAt as updatedAt
            from BlogPost p
            join p.author a
            where p.status = :status
              and (lower(p.title) like lower(concat('%', :keyword, '%'))
                   or lower(p.summary) like lower(concat('%', :keyword, '%')))
            order by p.publishedAt desc, p.id desc
            """)
    List<BlogPostSummaryView> searchPublished(@Param("keyword") String keyword,
                                              @Param("status") PostStatus status,
                                              Pageable pageable);

    /** 编辑 / 删除的唯一入口：带 author_id 条件，改不了别人的文章；查不到一律 404。 */
    Optional<BlogPost> findByIdAndAuthorId(Long id, Long authorId);

    /**
     * 删号级联。派生 deleteBy 会先 select 再逐条删，文章正文是 LONGTEXT，能一条 SQL 删完就别拉进内存。
     * 调用方（AdminUserService.delete）必须身处事务中。
     */
    @Modifying
    @Query("delete from BlogPost p where p.author.id = :authorId")
    int deleteByAuthorId(@Param("authorId") Long authorId);
}