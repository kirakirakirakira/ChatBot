package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.auth.UserStatus;
import com.chatbot.chatbot.dto.AdminUserVO;
import com.chatbot.chatbot.dto.BatchUserAction;
import com.chatbot.chatbot.dto.BatchUserRequest;
import com.chatbot.chatbot.dto.BatchUserResultVO;
import com.chatbot.chatbot.dto.ResetPasswordRequest;
import com.chatbot.chatbot.dto.ResetPasswordResult;
import com.chatbot.chatbot.dto.UpdateRoleRequest;
import com.chatbot.chatbot.dto.UpdateStatusRequest;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 管理台的批量操作：把 {@link AdminUserService} 里已有的单条动作，在一批 id 上跑一遍。
 * <p>
 * <b>为什么单独一个服务，而不是在 AdminUserService 里加个 batch 方法：</b>
 * 批量要求「逐条独立提交」（见下面的事务说明），而 Spring 的事务是**代理**实现的——
 * 同类内部的 this.updateStatus(...) 不走代理，@Transactional 会静默失效，
 * 结果就是整批挤在调用方的一个事务里（或者压根没有事务）。放在另一个 Bean 里，
 * 每次调用都是穿代理进去的，事务边界才是真的。
 * <p>
 * <b>事务与失败语义：</b>本方法**不带 @Transactional**，每一条都是一个独立事务。
 * 批量里「某几行注定失败」是常态：自己的行、层级不低于自己的行、最后一个启用的管理员。
 * 整批一个事务的话，勾选里混进一个不能动的人，剩下 19 个也全白干；
 * 逐条提交则成功的立刻落地、失败的把原因带回来，管理员照着结果改一下勾选再点一次就行。
 * 代价是「批量」不具备原子性——这是刻意的取舍，界面上会把逐条结果摊开给人看。
 * <p>
 * <b>规则不重写：</b>层级（只能上对下）、不能对自己下手、系统至少保留一个启用的管理员 / 超级管理员、
 * 审计留痕，全部由被调用的 AdminUserService 方法负责，本类一行都不重复实现。
 * 好处是以后改单条规则，批量自动跟着变，不会出现「单条拦住了、批量绕过去了」。
 * <p>
 * 审计：每条成功的操作由 AdminUserService 各写一行（动作、目标、操作者都齐），
 * 这里**不再补一条「批量」汇总行**——汇总行没有单一目标，塞进以「谁对谁做了什么」为形状的审计表里，
 * 反而会让同一次操作在列表里出现两次。
 */
@Service
public class AdminUserBatchService {

    /** 与列表分页的 MAX_PAGE_SIZE 同一个数：一屏最多 100 行，也就最多勾 100 个。 */
    private static final int MAX_BATCH_SIZE = 100;

    private final AdminUserService adminUserService;
    private final UserRepository userRepository;

    public AdminUserBatchService(AdminUserService adminUserService, UserRepository userRepository) {
        this.adminUserService = adminUserService;
        this.userRepository = userRepository;
    }

    public BatchUserResultVO execute(CurrentUser actor, BatchUserRequest request) {
        BatchUserAction action = BatchUserAction.of(request.action());
        if (action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "未知批量操作: " + request.action() + "（可用：" + BatchUserAction.availableNames() + "）");
        }
        List<Long> ids = normalizeIds(request.ids());
        // 整批级的参数校验放在循环之前：角色未知、密码太短这类错误和「具体哪个人」无关，
        // 一次性 400 比跑完 20 条、20 条都失败再逐条报同一句话干净得多。
        if (action == BatchUserAction.SET_ROLE && !Roles.isKnown(request.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知角色: " + request.role());
        }
        if (action == BatchUserAction.RESET_PASSWORD && !Boolean.TRUE.equals(request.generate())) {
            requireExplicitPassword(request.newPassword());
        }

        List<BatchUserResultVO.Item> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            items.add(runOne(actor, action, id, request));
        }
        return BatchUserResultVO.of(items);
    }

    /** 去重 + 去 null，保持勾选顺序。前端跨页多选、连点全选都可能带进重复 id，重复执行同一条只会白报错。 */
    private static List<Long> normalizeIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择一个用户");
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择一个用户");
        }
        if (distinct.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "单次批量操作最多 " + MAX_BATCH_SIZE + " 个用户，当前 " + distinct.size() + " 个");
        }
        return List.copyOf(distinct);
    }

    private BatchUserResultVO.Item runOne(CurrentUser actor, BatchUserAction action, Long id, BatchUserRequest request) {
        // 先读一次拿到用户名：失败结果里只有 id 的话，管理员根本不知道那一行是谁
        User target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return BatchUserResultVO.Item.fail(id, null, "用户不存在: " + id);
        }
        String username = target.getUsername();
        try {
            return switch (action) {
                case ENABLE -> ok(id, username,
                        adminUserService.updateStatus(actor, id, new UpdateStatusRequest(UserStatus.ENABLED)));
                case DISABLE -> ok(id, username,
                        adminUserService.updateStatus(actor, id, new UpdateStatusRequest(UserStatus.DISABLED)));
                case SET_ROLE -> ok(id, username,
                        adminUserService.updateRole(actor, id, new UpdateRoleRequest(request.role())));
                case RESET_PASSWORD -> resetPassword(actor, id, username, request);
                case REVOKE_SESSIONS -> {
                    adminUserService.revoke(actor, id);
                    yield BatchUserResultVO.Item.ok(id, username, null);
                }
                case DELETE -> {
                    adminUserService.delete(actor, id);
                    yield BatchUserResultVO.Item.ok(id, username, null);
                }
            };
        } catch (ResponseStatusException e) {
            // 业务失败（层级不够、是自己、最后一个管理员…）：reason 已经是中文，原样带回界面
            String reason = e.getReason();
            return BatchUserResultVO.Item.fail(id, username,
                    (reason == null || reason.isBlank()) ? "操作失败（HTTP " + e.getStatusCode().value() + "）" : reason);
        } catch (DataIntegrityViolationException e) {
            // 并发：别人刚把这一行删了 / 改了。文案与 GlobalExceptionHandler 保持同一口径，不泄漏表名约束名
            return BatchUserResultVO.Item.fail(id, username, "数据冲突，请刷新后重试");
        }
        // 刻意不 catch 别的 RuntimeException：真 bug 就该整批 500 炸出来，
        // 把它翻译成「这一条失败了」会让人以为只是数据问题（和项目「不加 catch-all」的取向一致）。
    }

    private BatchUserResultVO.Item resetPassword(CurrentUser actor, Long id, String username, BatchUserRequest request) {
        ResetPasswordResult result = adminUserService.resetPassword(actor, id,
                new ResetPasswordRequest(request.newPassword(), request.generate()));
        // 随机密码只在这一次响应里出现，必须逐条带回去：批量重置了 5 个人，
        // 界面得能列出「谁 → 什么密码」，管理员才好挨个通知
        return result.generatedPassword() == null
                ? BatchUserResultVO.Item.ok(id, username, null)
                : BatchUserResultVO.Item.okWithPassword(id, username, result.generatedPassword());
    }

    private static BatchUserResultVO.Item ok(Long id, String username, AdminUserVO updated) {
        return BatchUserResultVO.Item.ok(id, username, updated);
    }

    /** 与 AdminUserService.requireExplicitPassword 同一套规则，抄在这里只为「整批提前失败」。 */
    private static void requireExplicitPassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供新密码，或改用「随机生成」");
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码长度需在 6~64 之间");
        }
    }
}
