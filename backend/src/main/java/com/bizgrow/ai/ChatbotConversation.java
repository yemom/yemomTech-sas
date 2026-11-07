package com.YemomBizGrow.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chatbot_conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_chatbot_conv_org_session",
                columnNames = {"organization_id", "session_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChatbotConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "visitor_name", length = 128)
    private String visitorName;

    @Column(name = "visitor_email")
    private String visitorEmail;

    @Column(name = "visitor_phone", length = 64)
    private String visitorPhone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
