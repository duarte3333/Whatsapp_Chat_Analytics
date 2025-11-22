// package com.whatsappanalytics.whatsapp_analytics_backend.service;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.Mockito.*;

// import java.util.Collections;
// import java.util.Map;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.core.ParameterizedTypeReference;
// import org.springframework.http.HttpEntity;
// import org.springframework.http.HttpMethod;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.mock.web.MockMultipartFile;
// import org.springframework.web.client.RestTemplate;
// import org.springframework.web.multipart.MultipartFile;

// import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;
// import com.whatsappanalytics.whatsapp_analytics_backend.model.GroupChat;
// import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
// import com.whatsappanalytics.whatsapp_analytics_backend.repository.ChatMetricsRepository;
// import com.whatsappanalytics.whatsapp_analytics_backend.repository.GroupChatRepository;

// @ExtendWith(MockitoExtension.class)
// class ChatMetricsServiceTest {

//     @Mock
//     private ChatMetricsRepository chatMetricsRepository;

//     @Mock
//     private GroupChatRepository groupChatRepository;

//     @Mock
//     private RestTemplate restTemplate;

//     private ChatMetricsService chatMetricsService;

//     private MultipartFile file;
//     private User user;
//     private Map<String, Object> pythonStats;

//     // Typed ParameterizedTypeReference for Map<String, Object>
//     private final ParameterizedTypeReference<Map<String, Object>> typeRef =
//             new ParameterizedTypeReference<>() {
//             };

//     @BeforeEach
//     void setUp() {
//         file = new MockMultipartFile("file", "chat.txt", "text/plain", "Hello".getBytes());
//         user = new User("john", "john@example.com", "pass");
//         pythonStats = Map.of(
//                 "total_messages", 10,
//                 "top_user", "Alice",
//                 "participants_count", 3
//         );

//         chatMetricsService = new ChatMetricsService(chatMetricsRepository, groupChatRepository, restTemplate);
//     }


//     // -------------------------
//     // processAnonymousFile tests
//     // -------------------------
//     @Test
//     void processAnonymousFile_returnsStats() {
//         when(restTemplate.exchange(
//                 anyString(),
//                 eq(HttpMethod.POST),
//                 any(HttpEntity.class),
//                 eq(typeRef)
//         )).thenReturn(new ResponseEntity<>(pythonStats, HttpStatus.OK));

//         Map<String, Object> result = chatMetricsService.processAnonymousFile(file);

//         assertEquals(pythonStats, result);

//         verify(restTemplate).exchange(
//                 anyString(),
//                 eq(HttpMethod.POST),
//                 any(HttpEntity.class),
//                 eq(typeRef)
//         );
//     }

//     @Test
//     void processAnonymousFile_returnsEmptyMapOnNull() {
//         when(restTemplate.exchange(
//                 anyString(),
//                 eq(HttpMethod.POST),
//                 any(HttpEntity.class),
//                 eq(typeRef)
//         )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

//         Map<String, Object> result = chatMetricsService.processAnonymousFile(file);

//         assertTrue(result.isEmpty());
//     }

//     @Test
//     void processAnonymousFile_throwsRuntimeOnIOException() throws Exception {
//         MultipartFile badFile = mock(MultipartFile.class);
//         when(badFile.getBytes()).thenThrow(new java.io.IOException("fail"));

//         RuntimeException ex = assertThrows(RuntimeException.class,
//                 () -> chatMetricsService.processAnonymousFile(badFile));

//         assertTrue(ex.getMessage().contains("Failed to send file to Python service"));
//     }

//     // -------------------------
//     // processAndSaveMetrics tests
//     // -------------------------
//     @Test
//     void processAndSaveMetrics_savesChatMetrics() {
//         when(restTemplate.exchange(
//                 anyString(),
//                 eq(HttpMethod.POST),
//                 any(HttpEntity.class),
//                 eq(typeRef)
//         )).thenReturn(new ResponseEntity<>(pythonStats, HttpStatus.OK));

//         when(groupChatRepository.save(any(GroupChat.class))).thenAnswer(inv -> inv.getArgument(0));
//         when(chatMetricsRepository.save(any(ChatMetrics.class))).thenAnswer(inv -> inv.getArgument(0));

//         ChatMetrics cm = chatMetricsService.processAndSaveMetrics(file, user);

//         assertEquals(10, cm.getTotalMessages());
//         assertEquals("Alice", cm.getTopUser());
//         assertNotNull(cm.getGroupChatId());
//         assertEquals(user, cm.getGroupChatId().getUserId());

//         verify(restTemplate).exchange(
//                 anyString(),
//                 eq(HttpMethod.POST),
//                 any(HttpEntity.class),
//                 eq(typeRef)
//         );
//         verify(groupChatRepository).save(any(GroupChat.class));
//         verify(chatMetricsRepository).save(any(ChatMetrics.class));
//     }

//     @Test
//     void processAndSaveMetrics_throwsRuntimeOnEmptyStats() {
//         when(restTemplate.exchange(
//                 anyString(),
//                 eq(HttpMethod.POST),
//                 any(HttpEntity.class),
//                 eq(typeRef)
//         )).thenReturn(new ResponseEntity<>(Collections.emptyMap(), HttpStatus.OK));

//         RuntimeException ex = assertThrows(RuntimeException.class,
//                 () -> chatMetricsService.processAndSaveMetrics(file, user));

//         assertEquals("Python service returned no data", ex.getMessage());
//     }
// }
