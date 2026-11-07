package com.YemomBizGrow.user.dto;

import com.YemomBizGrow.user.User;
import com.YemomBizGrow.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The only shape a user is ever serialized in.
 * Password hash never appears — mapping happens explicitly here instead of
 * relying on Jackson annotations on the entity.
 *
 * @param id            account id
 * @param firstName     given name
 * @param lastName      family name
 * @param email         normalized e-mail
 * @param phoneNumber   optional international phone number
 * @param status        account lifecycle status
 * @param emailVerified whether e-mail verification completed (false until that flow ships)
 * @param createdAt     account creation instant (UTC)
 */
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserStatus status,
        boolean emailVerified,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
