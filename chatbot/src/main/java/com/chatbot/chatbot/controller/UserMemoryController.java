package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.CreateMemoryRequest;
import com.chatbot.chatbot.dto.UpdateMemoryRequest;
import com.chatbot.chatbot.dto.UserMemoryVO;
import com.chatbot.chatbot.service.MemoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 「只能管自己」的 AI 记忆接口。路径全部带 /me，不接受 userId 参数：
 * 只要有一个入口能从请求里拿到 id，它就是越权改别人记忆的洞。
 */
@RestController
@RequestMapping("/api/users/me/memories")
public class UserMemoryController {

    private final MemoryService memoryService;

    public UserMemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public List<UserMemoryVO> list(CurrentUser currentUser) {
        return memoryService.list(currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserMemoryVO create(CurrentUser currentUser, @Valid @RequestBody CreateMemoryRequest request) {
        return memoryService.create(currentUser, request);
    }

    @PutMapping("/{id}")
    public UserMemoryVO update(CurrentUser currentUser, @PathVariable Long id,
                               @Valid @RequestBody UpdateMemoryRequest request) {
        return memoryService.update(currentUser, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser currentUser, @PathVariable Long id) {
        memoryService.delete(currentUser, id);
    }

    /** 清空全部记忆。路径是集合本身，语义是「删除我的所有记忆」。 */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(CurrentUser currentUser) {
        memoryService.clear(currentUser);
    }
}
