package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.BlogPostSummaryVO;
import com.chatbot.chatbot.dto.BlogPostSummaryView;
import com.chatbot.chatbot.dto.BlogPostVO;
import com.chatbot.chatbot.dto.PageVO;
import com.chatbot.chatbot.dto.SaveBlogPostRequest;
import com.chatbot.chatbot.entity.BlogPost;
import com.chatbot.chatbot.entity.PostStatus;
import com.chatbot.chatbot.repository.BlogPostRepository;
import com.chatbot.chatbot.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 博客文章的业务逻辑。
 * <p>
 * 读：已发布文章全站可见；未发布的只有作者本人能在详情里看到（列表只有「我的文章」能看到）。
 * 写：只有作者能改 / 删，查不到和不是自己的统一 404——和会话归属校验同一口径，
 * 不告诉调用方「这篇存在但不是你的」。
 */
@Service
public class BlogService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    /** 从正文自动提取摘要的长度。120 字在列表卡片里大约两行，再长会被 CSS 截断。 */
    private static final int EXCERPT_LENGTH = 120;

    private final BlogPostRepository blogPostRepository;
    private final UserRepository userRepository;
    private final BlogPolicy blogPolicy;

    public BlogService(BlogPostRepository blogPostRepository,
                       UserRepository userRepository,
                       BlogPolicy blogPolicy) {
        this.blogPostRepository = blogPostRepository;
        this.userRepository = userRepository;
        this.blogPolicy = blogPolicy;
    }

    /** 新建。作者只来自 token；状态由 BlogPolicy 决定，Controller 不参与。 */
    @Transactional
    public BlogPostVO create(CurrentUser user, SaveBlogPostRequest request) {
        BlogPost post = new BlogPost();
        // 只写外键，不用 findById 再查一次 sys_user：拦截器这一请求里已经确认过账号存在
        post.setAuthor(userRepository.getReferenceById(user.id()));
        applyContent(post, request);
        PostStatus status = blogPolicy.initialStatus(user);
        post.setStatus(status);
        post.setPublishedAt(status == PostStatus.PUBLISHED ? LocalDateTime.now() : null);
        return BlogPostVO.from(blogPostRepository.save(post));
    }

    /** 已发布列表。不按当前用户过滤：博客就是给所有人看的。 */
    @Transactional(readOnly = true)
    public PageVO<BlogPostSummaryVO> listPublished(Integer page, Integer size) {
        Page<BlogPostSummaryView> result =
                blogPostRepository.findPageByStatus(PostStatus.PUBLISHED, pageable(page, size));
        return PageVO.of(result, BlogPostSummaryVO::from);
    }

    /** 我的文章：所有状态，按创建时间倒序。 */
    @Transactional(readOnly = true)
    public PageVO<BlogPostSummaryVO> listMine(CurrentUser user, Integer page, Integer size) {
        Page<BlogPostSummaryView> result =
                blogPostRepository.findPageByAuthorId(user.id(), pageable(page, size));
        return PageVO.of(result, BlogPostSummaryVO::from);
    }

    /** 详情：已发布人人可看；草稿 / 待审 / 驳回 / 下架只有作者本人能看，其他人 404。 */
    @Transactional(readOnly = true)
    public BlogPostVO detail(Long id, CurrentUser user) {
        BlogPost post = blogPostRepository.findById(id).orElseThrow(BlogService::notFound);
        if (post.getStatus() != PostStatus.PUBLISHED && !post.getAuthor().getId().equals(user.id())) {
            throw notFound();
        }
        return BlogPostVO.from(post);
    }

    /** 编辑：只能改自己的。状态与发布时间不变——改错别字不该让文章重新排队审核或重新冲上列表。 */
    @Transactional
    public BlogPostVO update(Long id, CurrentUser user, SaveBlogPostRequest request) {
        BlogPost post = requireOwned(id, user);
        applyContent(post, request);
        return BlogPostVO.from(blogPostRepository.save(post));
    }

    /** 删除：只能删自己的。 */
    @Transactional
    public void delete(Long id, CurrentUser user) {
        blogPostRepository.delete(requireOwned(id, user));
    }

    /**
     * 删号级联。由 AdminUserService.delete 调用：删用户行之前必须先删掉他的文章，
     * 否则 fk_blog_post_author 是 RESTRICT，会直接撞 errno 1451。
     */
    @Transactional
    public void deleteByAuthorId(Long authorId) {
        blogPostRepository.deleteByAuthorId(authorId);
    }

    private BlogPost requireOwned(Long id, CurrentUser user) {
        return blogPostRepository.findByIdAndAuthorId(id, user.id()).orElseThrow(BlogService::notFound);
    }

    private static void applyContent(BlogPost post, SaveBlogPostRequest request) {
        post.setTitle(request.title().trim());
        post.setContent(request.content());
        String summary = request.summary() == null ? "" : request.summary().trim();
        if (summary.isEmpty()) {
            summary = excerpt(request.content());
        }
        // 正文只有代码块 / 图片时提不出文字，存 NULL（前端回退显示「暂无摘要」），不存空串冒充有摘要
        post.setSummary(summary.isEmpty() ? null : summary);
    }

    /**
     * 从 Markdown 提纯文本摘要。只做「够列表卡片用」的粗略剥离，不追求完美：
     * 多留几个符号比把正文当 HTML 解析便宜得多，真正的渲染仍由前端 markdown-it 负责。
     */
    private static String excerpt(String markdown) {
        String text = markdown
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("(?s)~~~.*?~~~", " ")
                .replaceAll("`[^`]*`", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s{0,3}>\\s?", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("[*_~]", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, EXCERPT_LENGTH) + "…";
    }

    private static Pageable pageable(Integer page, Integer size) {
        int number = page == null ? 0 : page;
        int limit = size == null ? DEFAULT_PAGE_SIZE : size;
        if (number < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法: page 不能小于 0");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "分页参数不合法: size 需在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        // 排序写在 JPQL 里，这里不传 Sort，免得拼出两段 order by
        return PageRequest.of(number, limit);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
    }
}