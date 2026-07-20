package com.fintech.common.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** User Service'in servisler arası kullanım için sunduğu asgari kullanıcı görünümü. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSnapshot {
    private Long userId;
    private String username;
    private String displayName;
}
