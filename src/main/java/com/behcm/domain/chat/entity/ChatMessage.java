package com.behcm.domain.chat.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType;

    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chat_message_read_by", joinColumns = @JoinColumn(name = "chat_message_id"))
    @Column(name = "member_nickname")
    private Set<String> readBy = new HashSet<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_room", nullable = false)
    private WorkoutRoom workoutRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender", nullable = false)
    private Member sender;

    @Builder
    public ChatMessage(WorkoutRoom workoutRoom, Member sender, String content, MessageType messageType, String imageUrl) {
        this.content = content;
        this.messageType = messageType;
        this.imageUrl = imageUrl;
        this.workoutRoom = workoutRoom;
        this.sender = sender;
    }

    public void addReadBy(String nickname) {
        this.readBy.add(nickname);
    }
}