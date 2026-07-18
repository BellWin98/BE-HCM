package com.behcm.domain.chat.service;

import com.behcm.domain.chat.dto.ChatHistoryResponse;
import com.behcm.domain.chat.dto.ChatImageUploadResponse;
import com.behcm.domain.chat.dto.ChatMessageRequest;
import com.behcm.domain.chat.dto.ChatMessageResponse;
import com.behcm.domain.chat.dto.ReadStatusMessage;
import com.behcm.domain.chat.entity.ChatMessage;
import com.behcm.domain.chat.entity.MessageType;
import com.behcm.domain.chat.repository.ChatMessageRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.config.aws.S3Service;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Mock
    private WorkoutRoomRepository workoutRoomRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatService chatService;

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Member member(long id, String nickname) {
        Member m = Member.builder()
                .email(nickname + "@test.com")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        setId(m, id);
        return m;
    }

    private WorkoutRoom room(long id, Member owner) {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(owner)
                .build();
        setId(room, id);
        return room;
    }

    private WorkoutRoomMember wrm(long id, Member member, WorkoutRoom room) {
        WorkoutRoomMember wrm = WorkoutRoomMember.builder().member(member).workoutRoom(room).build();
        setId(wrm, id);
        return wrm;
    }

    private ChatMessage chatMessage(long id, WorkoutRoom room, Member sender, MessageType type, String content) {
        ChatMessage message = ChatMessage.builder()
                .workoutRoom(room)
                .sender(sender)
                .content(content)
                .messageType(type)
                .build();
        setId(message, id);
        return message;
    }

    // ---------- uploadChatImage ----------

    @Test
    @DisplayName("uploadChatImage는 운동방이 없으면 WORKOUT_ROOM_NOT_FOUND 예외를 던진다")
    void uploadChatImage_roomNotFound_throwsWorkoutRoomNotFound() {
        Member member = member(1L, "user");
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> chatService.uploadChatImage(member, 1L, file))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ROOM_NOT_FOUND);

        verify(s3Service, never()).uploadChatImage(any(), anyLong());
    }

    @Test
    @DisplayName("uploadChatImage는 방 멤버가 아니면 NOT_WORKOUT_ROOM_MEMBER 예외를 던진다")
    void uploadChatImage_notMember_throwsNotWorkoutRoomMember() {
        Member owner = member(1L, "owner");
        Member outsider = member(2L, "outsider");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(outsider, room)).willReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> chatService.uploadChatImage(outsider, 1L, file))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_WORKOUT_ROOM_MEMBER);
    }

    @Test
    @DisplayName("uploadChatImage는 정상 멤버이면 S3 업로드 URL을 응답에 담는다")
    void uploadChatImage_member_returnsUploadedUrl() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(owner, room)).willReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        given(s3Service.uploadChatImage(file, 1L)).willReturn("https://s3/chat/1/a.png");

        ChatImageUploadResponse response = chatService.uploadChatImage(owner, 1L, file);

        assertThat(response.getImageUrl()).isEqualTo("https://s3/chat/1/a.png");
    }

    // ---------- sendMessage ----------

    @Test
    @DisplayName("sendMessage는 운동방이 없으면 WORKOUT_ROOM_NOT_FOUND 예외를 던진다")
    void sendMessage_roomNotFound_throwsWorkoutRoomNotFound() {
        Member member = member(1L, "user");
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.empty());
        ChatMessageRequest request = new ChatMessageRequest();
        request.setType(MessageType.TEXT);
        request.setContent("hi");

        assertThatThrownBy(() -> chatService.sendMessage(1L, member, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ROOM_NOT_FOUND);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendMessage는 방 멤버가 아니면 NOT_WORKOUT_ROOM_MEMBER 예외를 던진다")
    void sendMessage_notMember_throwsNotWorkoutRoomMember() {
        Member owner = member(1L, "owner");
        Member outsider = member(2L, "outsider");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(outsider, room)).willReturn(false);
        ChatMessageRequest request = new ChatMessageRequest();
        request.setType(MessageType.TEXT);
        request.setContent("hi");

        assertThatThrownBy(() -> chatService.sendMessage(1L, outsider, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_WORKOUT_ROOM_MEMBER);
    }

    @Test
    @DisplayName("sendMessage는 IMAGE 타입인데 imageUrl이 비어있으면 INVALID_INPUT 예외를 던진다")
    void sendMessage_imageTypeWithoutUrl_throwsInvalidInput() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(owner, room)).willReturn(true);
        ChatMessageRequest request = new ChatMessageRequest();
        request.setType(MessageType.IMAGE);
        request.setImageUrl("   ");

        assertThatThrownBy(() -> chatService.sendMessage(1L, owner, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendMessage는 정상 TEXT 메시지를 저장하고 /topic/chat/room/{roomId}로 브로드캐스트한다")
    void sendMessage_textMessage_savesAndBroadcasts() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(owner, room)).willReturn(true);
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room)).willReturn(List.of());
        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            setId(saved, 100L);
            return saved;
        });
        ChatMessageRequest request = new ChatMessageRequest();
        request.setType(MessageType.TEXT);
        request.setContent("안녕하세요");

        chatService.sendMessage(1L, owner, request);

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/chat/room/1"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("안녕하세요");
        assertThat(captor.getValue().getSender()).isEqualTo("owner");
    }

    @Test
    @DisplayName("sendMessage는 IMAGE 메시지의 imageUrl 앞뒤 공백을 제거해 저장한다")
    void sendMessage_imageMessage_trimsImageUrl() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(owner, room)).willReturn(true);
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room)).willReturn(List.of());
        ArgumentCaptor<ChatMessage> savedCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        given(chatMessageRepository.save(savedCaptor.capture())).willAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            setId(saved, 101L);
            return saved;
        });
        ChatMessageRequest request = new ChatMessageRequest();
        request.setType(MessageType.IMAGE);
        request.setImageUrl("  https://s3/img.png  ");

        chatService.sendMessage(1L, owner, request);

        assertThat(savedCaptor.getValue().getImageUrl()).isEqualTo("https://s3/img.png");
    }

    // ---------- getChatHistory ----------

    @Test
    @DisplayName("getChatHistory는 운동방이 없으면 WORKOUT_ROOM_NOT_FOUND 예외를 던진다")
    void getChatHistory_roomNotFound_throwsWorkoutRoomNotFound() {
        Member member = member(1L, "user");
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getChatHistory(member, 1L, null, 20))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("getChatHistory는 방 멤버가 아니면 NOT_WORKOUT_ROOM_MEMBER 예외를 던진다")
    void getChatHistory_notMember_throwsNotWorkoutRoomMember() {
        Member owner = member(1L, "owner");
        Member outsider = member(2L, "outsider");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.findByWorkoutRoomAndMember(room, outsider)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getChatHistory(outsider, 1L, null, 20))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_WORKOUT_ROOM_MEMBER);
    }

    @Test
    @DisplayName("getChatHistory는 cursorId가 null이면 마지막으로 읽은 메시지 이후 새 메시지와 이전 메시지를 합쳐 반환한다")
    void getChatHistory_initialLoad_combinesNewAndOldMessages() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        WorkoutRoomMember wrm = wrm(1L, owner, room);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.findByWorkoutRoomAndMember(room, owner)).willReturn(Optional.of(wrm));

        ChatMessage newMsg = chatMessage(5L, room, owner, MessageType.TEXT, "new");
        given(chatMessageRepository.findByWorkoutRoomAndIdGreaterThanOrderByIdAsc(room, 0L))
                .willReturn(List.of(newMsg));

        ChatMessage oldMsg = chatMessage(3L, room, owner, MessageType.TEXT, "old");
        given(chatMessageRepository.findByWorkoutRoomAndIdLessThanOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(room), org.mockito.ArgumentMatchers.eq(1L), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(oldMsg), Pageable.ofSize(20), true));

        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room)).willReturn(List.of(wrm));

        ChatHistoryResponse response = chatService.getChatHistory(owner, 1L, null, 20);

        assertThat(response.getMessages()).extracting(ChatMessageResponse::getContent)
                .containsExactly("old", "new");
        assertThat(response.getNextCursorId()).isEqualTo(3L);
        assertThat(response.isHasNext()).isTrue();
    }

    @Test
    @DisplayName("getChatHistory는 cursorId가 있으면 해당 커서보다 이전 메시지를 조회한다")
    void getChatHistory_withCursor_loadsOlderMessages() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        WorkoutRoomMember wrm = wrm(1L, owner, room);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.findByWorkoutRoomAndMember(room, owner)).willReturn(Optional.of(wrm));

        ChatMessage olderMsg = chatMessage(2L, room, owner, MessageType.TEXT, "older");
        given(chatMessageRepository.findByWorkoutRoomAndIdLessThanOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(room), org.mockito.ArgumentMatchers.eq(10L), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(olderMsg), Pageable.ofSize(20), false));
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room)).willReturn(List.of(wrm));

        ChatHistoryResponse response = chatService.getChatHistory(owner, 1L, 10L, 20);

        assertThat(response.getMessages()).extracting(ChatMessageResponse::getContent).containsExactly("older");
        assertThat(response.getNextCursorId()).isEqualTo(2L);
        assertThat(response.isHasNext()).isFalse();
        verify(chatMessageRepository, never()).findByWorkoutRoomAndIdGreaterThanOrderByIdAsc(any(), any());
    }

    // ---------- getChatHistoryForAdmin ----------

    @Test
    @DisplayName("getChatHistoryForAdmin은 비활성화된 방도 조회할 수 있다 (isActive 필터 없음)")
    void getChatHistoryForAdmin_roomNotFound_throwsWorkoutRoomNotFound() {
        given(workoutRoomRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getChatHistoryForAdmin(1L, null, 20))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("getChatHistoryForAdmin은 size를 1~50 범위로 clamp하고 cursorId가 없으면 최신 메시지를 반환한다")
    void getChatHistoryForAdmin_clampsSizeAndLoadsLatest() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findById(1L)).willReturn(Optional.of(room));

        ChatMessage msg = chatMessage(9L, room, owner, MessageType.TEXT, "latest");
        given(chatMessageRepository.findByWorkoutRoomOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(room), any(Pageable.class)))
                .willReturn(List.of(msg));
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room)).willReturn(List.of());

        ChatHistoryResponse response = chatService.getChatHistoryForAdmin(1L, null, 999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatMessageRepository).findByWorkoutRoomOrderByIdDesc(org.mockito.ArgumentMatchers.eq(room), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(51); // clamp(999 -> 50) + 1
        assertThat(response.getMessages()).extracting(ChatMessageResponse::getContent).containsExactly("latest");
    }

    // ---------- updateLastReadMessage / markAsRead ----------

    @Test
    @DisplayName("updateLastReadMessage는 채팅 메시지가 없으면 아무 것도 하지 않는다")
    void updateLastReadMessage_noMessages_doesNothing() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        WorkoutRoomMember wrm = wrm(1L, owner, room);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.findByWorkoutRoomAndMember(room, owner)).willReturn(Optional.of(wrm));
        given(chatMessageRepository.findFirstByWorkoutRoomOrderByIdDesc(room)).willReturn(null);

        chatService.updateLastReadMessage(owner, 1L);

        verify(workoutRoomMemberRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), any(Object.class));
    }

    @Test
    @DisplayName("updateLastReadMessage는 새 메시지가 있으면 lastReadMessage를 갱신하고 읽음 상태를 브로드캐스트한다")
    void updateLastReadMessage_newerMessage_updatesAndBroadcasts() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        WorkoutRoomMember wrm = wrm(1L, owner, room);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.findByWorkoutRoomAndMember(room, owner)).willReturn(Optional.of(wrm));

        ChatMessage latest = chatMessage(7L, room, owner, MessageType.TEXT, "latest");
        given(chatMessageRepository.findFirstByWorkoutRoomOrderByIdDesc(room)).willReturn(latest);
        given(chatMessageRepository.findByWorkoutRoomOrderByIdDesc(org.mockito.ArgumentMatchers.eq(room), any(Pageable.class)))
                .willReturn(List.of(latest));
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room)).willReturn(List.of(wrm));

        chatService.updateLastReadMessage(owner, 1L);

        assertThat(wrm.getLastReadMessage()).isEqualTo(latest);
        verify(workoutRoomMemberRepository).save(wrm);
        ArgumentCaptor<ReadStatusMessage> captor = ArgumentCaptor.forClass(ReadStatusMessage.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/chat/room/1"), captor.capture());
        assertThat(captor.getValue().getUpdatedMessages()).hasSize(1);
        assertThat(captor.getValue().getUpdatedMessages().get(0).getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("markAsRead는 updateLastReadMessage와 동일하게 동작한다")
    void markAsRead_delegatesToUpdateLastReadMessage() {
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        WorkoutRoomMember wrm = wrm(1L, owner, room);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.findByWorkoutRoomAndMember(room, owner)).willReturn(Optional.of(wrm));
        ChatMessage latest = chatMessage(7L, room, owner, MessageType.TEXT, "latest");
        given(chatMessageRepository.findFirstByWorkoutRoomOrderByIdDesc(room)).willReturn(latest);
        given(chatMessageRepository.findByWorkoutRoomOrderByIdDesc(org.mockito.ArgumentMatchers.eq(room), any(Pageable.class)))
                .willReturn(List.of(latest));
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room)).willReturn(List.of(wrm));

        chatService.markAsRead(1L, 7L, owner);

        assertThat(wrm.getLastReadMessage()).isEqualTo(latest);
        verify(workoutRoomMemberRepository).save(wrm);
    }

    @Test
    @DisplayName("toResponsesWithUnread는 아직 읽지 않은 멤버 수만큼 unreadCount를 계산한다")
    void sendMessage_unreadCount_countsMembersWhoHaveNotReadYet() {
        Member owner = member(1L, "owner");
        Member reader = member(2L, "reader");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(owner, room)).willReturn(true);

        WorkoutRoomMember ownerWrm = wrm(1L, owner, room);
        WorkoutRoomMember readerWrm = wrm(2L, reader, room);
        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            setId(saved, 200L);
            return saved;
        });
        // reader already read up to message 200 (>= new message id) -> not counted as unread
        ChatMessage alreadyReadMarker = chatMessage(200L, room, owner, MessageType.TEXT, "marker");
        readerWrm.setLastReadMessage(alreadyReadMarker);
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room))
                .willReturn(List.of(ownerWrm, readerWrm));

        ChatMessageRequest request = new ChatMessageRequest();
        request.setType(MessageType.TEXT);
        request.setContent("hello");

        chatService.sendMessage(1L, owner, request);

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/chat/room/1"), captor.capture());
        // owner(lastRead=null) hasn't read -> unread; reader already read up to 200 -> not unread
        assertThat(captor.getValue().getUnreadCount()).isEqualTo(1);
    }
}
