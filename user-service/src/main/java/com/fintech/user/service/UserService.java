package com.fintech.user.service;

import com.fintech.common.exception.ResourceNotFoundException;
import com.fintech.user.dto.AuthDto;
import com.fintech.user.entity.User;
import com.fintech.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public AuthDto.UserInfo getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", "id", id));
        return toUserInfo(user);
    }

    public AuthDto.UserInfo getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", "username", username));
        return toUserInfo(user);
    }

    public List<AuthDto.UserInfo> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserInfo)
                .toList();
    }

    @Transactional
    public AuthDto.UserInfo updateUser(Long id, AuthDto.RegisterRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", "id", id));

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());

        user = userRepository.save(user);
        log.info("Kullanıcı güncellendi: {}", user.getUsername());
        return toUserInfo(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", "id", id));
        user.setStatus("INACTIVE");
        userRepository.save(user);
        log.info("Kullanıcı deaktif edildi: {}", user.getUsername());
    }

    /**
     * Diğer servisler tarafından Feign ile çağrılır.
     * Gateway X-User-Id header'ından gelen userId ile kullanıcı doğrulaması.
     */
    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }

    private AuthDto.UserInfo toUserInfo(User user) {
        return AuthDto.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();
    }
}
