package com.fintech.user.controller;

import com.fintech.common.dto.internal.UserSnapshot;
import com.fintech.common.dto.response.ApiResponse;
import com.fintech.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gateway tarafından yayınlanmayan, yalnızca servis ağına açık kullanıcı sözleşmesi. */
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserSnapshot>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getInternalUserSnapshot(userId)));
    }
}
