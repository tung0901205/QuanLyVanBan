package com.qlda.aiservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlda.aiservice.dto.common.ApiSuccessResponse;
import com.qlda.aiservice.dto.request.ChatbotAskRequest;
import com.qlda.aiservice.dto.request.ChatbotFeedbackRequest;
import com.qlda.aiservice.entity.AiResultEntity;
import com.qlda.aiservice.repository.AiResultRepository;
import com.qlda.aiservice.security.CurrentUserService;
import com.qlda.aiservice.exception.AppException;
import com.qlda.aiservice.exception.ErrorCode;
import com.qlda.aiservice.service.chatbot.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final AiResultRepository aiResultRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;

    @PostMapping({"/chat", "/chatbot/ask"})
    public ResponseEntity<ApiSuccessResponse<Map<String, Object>>> ask(@Valid @RequestBody ChatbotAskRequest request) {
        return ResponseEntity.ok(ApiSuccessResponse.of("Chatbot response successfully", chatbotService.ask(request)));
    }

    @PostMapping("/chatbot/feedback")
    public ResponseEntity<ApiSuccessResponse<Map<String, Object>>> feedback(@Valid @RequestBody ChatbotFeedbackRequest request) {
        Long currentUserId = currentUserService.requireUserId();
        return aiResultRepository.findById(request.resultId())
                .map(entity -> {
                    if (!currentUserService.isAdmin() && !java.util.Objects.equals(currentUserId, entity.getNguoiYeuCauID())) {
                        throw new AppException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                            "Only the result owner can submit feedback");
                    }
                    String updatedNote = appendFeedback(entity.getGhiChu(), request.feedback(), request.comment());
                    entity.setGhiChu(updatedNote);
                    aiResultRepository.save(entity);
                    Map<String, Object> result = Map.of(
                            "resultId", request.resultId(),
                            "feedback", request.feedback(),
                            "recorded", true
                    );
                    return ResponseEntity.ok(ApiSuccessResponse.of("Feedback recorded", result));
                })
                .orElseGet(() -> {
                    Map<String, Object> result = Map.of("resultId", request.resultId(), "recorded", false);
                    return ResponseEntity.ok(ApiSuccessResponse.of("Result not found", result));
                });
    }

    @SuppressWarnings("unchecked")
    private String appendFeedback(String existingNote, String feedback, String comment) {
        Map<String, Object> noteMap = new LinkedHashMap<>();
        if (existingNote != null && !existingNote.isBlank()) {
            try {
                noteMap.putAll(objectMapper.readValue(existingNote, Map.class));
            } catch (Exception ignored) {}
        }
        noteMap.put("feedback", feedback);
        if (comment != null && !comment.isBlank()) noteMap.put("feedbackComment", comment);
        try {
            return objectMapper.writeValueAsString(noteMap);
        } catch (JsonProcessingException e) {
            return existingNote;
        }
    }
}
