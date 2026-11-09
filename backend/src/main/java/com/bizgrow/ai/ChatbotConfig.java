package com.YemomBizGrow.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "chatbot_configs")
@Getter
@Setter
@NoArgsConstructor
public class ChatbotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private UUID organizationId;

    @Column(name = "bot_name", nullable = false, length = 128)
    private String botName = "YemomBizGrow Assistant";

    @Column(name = "greeting_message", nullable = false, columnDefinition = "TEXT")
    private String greetingMessage = "Hello! How can we assist your business today?";

    @Column(name = "prompt_instructions", columnDefinition = "TEXT")
    private String promptInstructions;

    @Column(name = "primary_color", nullable = false, length = 32)
    private String primaryColor = "#0f766e";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "faq_items")
    private List<Map<String, Object>> faqItems;

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
