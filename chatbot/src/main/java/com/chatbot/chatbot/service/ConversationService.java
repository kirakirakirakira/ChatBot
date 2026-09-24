package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.ConversationVO;
import com.chatbot.chatbot.dto.MessagePageVO;
import com.chatbot.chatbot.dto.RenameConversationRequest;
import com.chatbot.chatbot.dto.MessageVO;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.Role;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import com.chatbot.chatbot.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话的增删查 + 消息分页。
 * <p>
 * 每个公开方法都要 {@link CurrentUser}：会话按用户隔离，归属校验只在这一层做，
 * 控制器只负责把当前用户传进来，repository 只提供带 owner_id 条件的查询。
 */
@Service
public class ConversationService {

    /** 单页消息条数默认值。50 条足够铺满一屏多，再往上翻靠「加载更早的消息」。 */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * 单页上限。挡的是 {@code ?limit=999999} 这种把整段历史一次拖走的请求：
     * content 是 LONGTEXT，不设上限等于把分页白做了。
     */
    private static final int MAX_PAGE_SIZE = 200;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /**
     * 建会话，归属就是当前登录用户。
     * 用 getReferenceById 拿代理而不是 findById：只需要 owner_id 这一列，
     * 不必为了写外键再查一次 sys_user（AuthInterceptor 这一请求里已经确认过账号存在）。
     */
    public ConversationVO create(CurrentUser user) {
        Conversation conversation = new Conversation();
        conversation.setTitle("新的对话");
        conversation.setOwner(userRepository.getReferenceById(user.id()));
        return ConversationVO.from(conversationRepository.save(conversation));
    }

    public List<ConversationVO> list(CurrentUser user) {
        return conversationRepository.findAllByOwnerIdOrderByUpdatedAtDesc(user.id()).stream()
                .map(ConversationVO::from)
                .toList();
    }

    /**
     * 分页取消息，从最新往前翻。
     *
     * @param beforeId 游标：只返回 id 小于它的消息；null 表示取最新一页
     * @param limit    单页条数；null 或 &lt;=0 用默认值，超过上限按上限截断
     */
    public MessagePageVO messages(Long id, CurrentUser user, Long beforeId, Integer limit) {
        requireOwned(id, user);

        int size = normalizeLimit(limit);
        // 多要一条用来判断还有没有更早的，省掉一次 count(*)
        Pageable page = PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, "id"));
        List<Message> rows = (beforeId == null)
                ? messageRepository.findByConversationId(id, page)
                : messageRepository.findByConversationIdAndIdLessThan(id, beforeId, page);

        boolean hasMore = rows.size() > size;
        List<Message> window = hasMore ? rows.subList(0, size) : rows;

        // 查出来是 id 倒序（最新在前），反转成时间正序再交给前端，前端不用再自己倒一遍
        List<MessageVO> items = new ArrayList<>(window.stream().map(MessageVO::from).toList());
        Collections.reverse(items);

        Long cursor = (hasMore && !items.isEmpty()) ? items.get(0).id() : null;
        return new MessagePageVO(items, cursor, hasMore);
    }

    /**
     * 重命名。trim 后入库：标题要进侧边栏展示和「还是不是默认标题」的判断，首尾空格只会造成「看起来一样却不相等」。
     * 返回 VO 让前端直接覆盖本地那一条，不用重拉整个列表。
     */
    @Transactional
    public ConversationVO rename(Long id, CurrentUser user, RenameConversationRequest request) {
        String title = request.title().strip();
        int updated = conversationRepository.updateTitle(id, user.id(), title);
        if (updated == 0) {
            // 和 requireOwned 同一个口径：不存在或不是自己的，都是 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + id);
        }
        Conversation conversation = conversationRepository.findByIdAndOwnerId(id, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + id));
        return ConversationVO.from(conversation);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        Conversation conversation = requireOwned(id, user);
        messageRepository.deleteByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }

    /**
     * 重新生成的前置动作：删掉该会话最后一条助手消息，返回它前面最近一条用户消息的正文（重跑生成要用）。
     * <p>
     * 最后一条不是助手消息（比如用户刚发完、回复还没开始）就 400：
     * 这种情况前端本来就不该显示「重新生成」按钮，真调到了说明界面状态和后端不一致，别默默替它兜。
     * <p>
     * 用户消息不删也不重存：它已经在库里，重跑时历史里自然带着它，而旧回答已经不在历史里了。
     */
    /** 重新生成前置的返回值：被删回答前面那条用户消息的正文 + 被删回答当时用的模型（重跑默认沿用）。 */
    public record DroppedReply(String prompt, String model) {
    }

    @Transactional
    public DroppedReply dropLastAssistantMessage(Long conversationId) {
        List<Message> all = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        Message last = all.isEmpty() ? null : all.get(all.size() - 1);
        if (last == null || last.getRole() != Role.ASSISTANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最后一条不是助手消息，无法重新生成");
        }
        String prompt = null;
        for (int i = all.size() - 2; i >= 0; i--) {
            if (all.get(i).getRole() == Role.USER) {
                prompt = all.get(i).getContent();
                break;
            }
        }
        if (prompt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "找不到要重跑的用户消息，无法重新生成");
        }
        messageRepository.deleteById(last.getId());
        return new DroppedReply(prompt, last.getModel());
    }

    /**
     * 取当前用户的会话；不存在<b>或属于别人</b>都抛 404。
     * <p>
     * 刻意不用 403：403 会把「这个 id 确实存在」泄露出去，而 id 是自增的，
     * 任何登录用户都能靠它枚举出全站有多少会话。404 让两种情况在响应上完全一样。
     */
    public Conversation requireOwned(Long id, CurrentUser user) {
        return conversationRepository.findByIdAndOwnerId(id, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + id));
    }

    /** 非法 limit 不报错，按默认值/上限收进来：翻页参数不值得让用户看到一个 400。 */
    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }
}
