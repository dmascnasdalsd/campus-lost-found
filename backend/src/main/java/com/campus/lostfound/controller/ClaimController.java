package com.campus.lostfound.controller;

import com.campus.lostfound.dto.ApiResponse;
import com.campus.lostfound.dto.ClaimView;
import com.campus.lostfound.dto.CreateClaimRequest;
import com.campus.lostfound.dto.RejectClaimRequest;
import com.campus.lostfound.service.ClaimService;
import com.campus.lostfound.support.AuthSupport;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认领申请的 HTTP 入口：只做参数校验、取当前用户 id、转发给 Service。
 * 这些路由均落在 SecurityConfig 的 anyRequest().authenticated() 之下，天然需要登录。
 */
@RestController
@RequestMapping("/api")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    /** 对某启事发起认领申请。 */
    @PostMapping("/reports/{id}/claims")
    public ApiResponse<ClaimView> create(@PathVariable Long id,
                                         @Valid @RequestBody CreateClaimRequest request,
                                         Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(claimService.create(userId, id, request));
    }

    /** 我的认领：role=applied（缺省）/ received。 */
    @GetMapping("/claims")
    public ApiResponse<List<ClaimView>> list(@RequestParam(required = false) String role,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(claimService.list(userId, role));
    }

    /** 撤回自己待处理的认领申请。 */
    @PostMapping("/claims/{id}/withdraw")
    public ApiResponse<ClaimView> withdraw(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(claimService.withdraw(userId, id));
    }

    /** 接受认领申请：仅启事发布者或管理员。 */
    @PostMapping("/claims/{id}/accept")
    public ApiResponse<ClaimView> accept(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(claimService.accept(userId, AuthSupport.isAdmin(authentication), id));
    }

    /** 驳回认领申请：仅启事发布者或管理员，且必须填写驳回原因。 */
    @PostMapping("/claims/{id}/reject")
    public ApiResponse<ClaimView> reject(@PathVariable Long id,
                                         @Valid @RequestBody RejectClaimRequest request,
                                         Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(claimService.reject(userId, AuthSupport.isAdmin(authentication), id,
                request.rejectReason()));
    }
}
