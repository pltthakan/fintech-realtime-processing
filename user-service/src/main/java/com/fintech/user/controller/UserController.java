package com.fintech.user.controller;

import com.fintech.common.dto.response.ApiResponse;
import com.fintech.user.dto.AuthDto;
import com.fintech.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuthDto.UserInfo>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<ApiResponse<AuthDto.UserInfo>> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByUsername(username)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuthDto.UserInfo>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AuthDto.UserInfo>> updateUser(
            @PathVariable Long id,
            @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(id, request), "Kullanıcı güncellendi"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Kullanıcı deaktif edildi"));
    }

    /** Diğer servislerin internal çağrısı - kullanıcı var mı kontrolü */
    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> existsById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.existsById(id));
    }
}
