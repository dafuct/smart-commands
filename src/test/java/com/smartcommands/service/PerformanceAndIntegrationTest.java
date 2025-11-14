package com.smartcommands.service;

import com.smartcommands.model.CommandHistory;
import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.parser.CommandParser;
import com.smartcommands.repository.CommandHistoryRepository;
import com.smartcommands.repository.CommandMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Performance and Integration Tests for Smart Commands Services
 */
@ExtendWith(SpringExtension.class)
@DisplayName("Performance and Integration Tests")
class PerformanceAndIntegrationTest {

    @Mock
    private OllamaService ollamaService;

    @Mock
    private CommandHistoryRepository commandHistoryRepository;

    private CommandProcessorService commandProcessor;
    private IntelligentCommandValidator intelligentValidator;
    private StructuralCommandValidator structuralValidator;
    private FallbackCommandValidator fallbackValidator;
    private CommandParser commandParser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Initialize real components for integration testing
        when(ollamaService.isOllamaRunning()).thenReturn(false);
        commandParser = new CommandParser(ollamaService);
        CommandMetadataRepository metadataRepository = new CommandMetadataRepository();
        structuralValidator = new StructuralCommandValidator(commandParser, metadataRepository);
        intelligentValidator = new IntelligentCommandValidator(
            ollamaService, commandParser, structuralValidator);
        fallbackValidator = new FallbackCommandValidator();
        
        commandProcessor = new CommandProcessorService(
            ollamaService, commandHistoryRepository, intelligentValidator);
    }

    // Performance Tests

    @Test
    @DisplayName("Should handle high volume command processing")
    void testHighVolumeCommandProcessing() throws InterruptedException {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        int commandCount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        AtomicInteger successCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < commandCount; i++) {
            final int commandIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    CommandSuggestion suggestion = commandProcessor.processInput("echo " + commandIndex);
                    if (suggestion.getType() == CommandSuggestion.SuggestionType.REGULAR) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Log but don't fail the test
                    System.err.println("Error processing command " + commandIndex + ": " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all commands to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime.get();

        assertTrue(successCount.get() >= commandCount * 0.95, 
            "At least 95% of commands should succeed: " + successCount.get() + "/" + commandCount);
        assertTrue(duration < 10000, 
            "Should process " + commandCount + " commands in under 10 seconds, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Should handle memory pressure with large commands")
    void testMemoryPressureWithLargeCommands() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        // Process commands with increasing size to test memory handling
        for (int i = 0; i < 100; i++) {
            String largeCommand = "echo " + "x".repeat(i * 100);
            CommandSuggestion suggestion = commandProcessor.processInput(largeCommand);
            assertNotNull(suggestion);
            assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        }

        // Force garbage collection and check we're still responsive
        System.gc();
        
        CommandSuggestion finalSuggestion = commandProcessor.processInput("echo test");
        assertNotNull(finalSuggestion);
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, finalSuggestion.getType());
    }

    @Test
    @DisplayName("Should maintain performance under concurrent load")
    void testConcurrentLoadPerformance() throws InterruptedException {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        int threadCount = 50;
        int commandsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger totalCommands = new AtomicInteger(0);
        AtomicInteger successfulCommands = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int c = 0; c < commandsPerThread; c++) {
                    try {
                        totalCommands.incrementAndGet();
                        CommandSuggestion suggestion = commandProcessor.processInput(
                            "docker ps -" + c);
                        if (suggestion.getType() == CommandSuggestion.SuggestionType.REGULAR) {
                            successfulCommands.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Error in concurrent test: " + e.getMessage());
                    }
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        int expectedCommands = threadCount * commandsPerThread;

        assertTrue(successfulCommands.get() >= expectedCommands * 0.9,
            "At least 90% of commands should succeed: " + successfulCommands.get() + "/" + expectedCommands);
        assertTrue(duration < 15000,
            "Should complete concurrent load in under 15 seconds, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Should handle cache performance under load")
    void testCachePerformanceUnderLoad() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any())).thenReturn("ps");

        // Test cache performance with repeated commands
        String[] testCommands = {"docker ps", "git status", "kubectl get pods", "npm install"};
        
        long startTime = System.currentTimeMillis();
        
        // First pass - populate cache
        for (int i = 0; i < 100; i++) {
            String command = testCommands[i % testCommands.length];
            CommandSuggestion suggestion = intelligentValidator.validateAndCorrect(command);
            assertNotNull(suggestion);
        }
        
        long firstPassTime = System.currentTimeMillis() - startTime;
        
        // Second pass - should benefit from cache
        startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            String command = testCommands[i % testCommands.length];
            CommandSuggestion suggestion = intelligentValidator.validateAndCorrect(command);
            assertNotNull(suggestion);
        }
        
        long secondPassTime = System.currentTimeMillis() - startTime;
        
        // Second pass should be faster due to caching (allowing some variance)
        assertTrue(secondPassTime <= firstPassTime * 1.5,
            "Cached operations should be faster or similar. First: " + firstPassTime + "ms, Second: " + secondPassTime + "ms");
    }

    // Integration Tests

    @Test
    @DisplayName("Should integrate all services for complete validation flow")
    void testCompleteValidationFlowIntegration() {
        // Mock Ollama as available for full integration
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any())).thenReturn("ps");

        // Test complete flow with correction
        CommandSuggestion suggestion = commandProcessor.processInput("docker sp -a");
        
        assertNotNull(suggestion);
        assertTrue(suggestion.needsCorrection());
        assertEquals("docker sp -a", suggestion.getOriginalInput());
        assertNotNull(suggestion.getSuggestion());
        
        // Verify history was saved
        verify(commandHistoryRepository, times(1)).save(any(CommandHistory.class));
    }

    @Test
    @DisplayName("Should handle service degradation gracefully")
    void testServiceDegradationGracefulHandling() {
        // Start with Ollama available, then make it fail
        when(ollamaService.isOllamaRunning())
            .thenReturn(true)   // First call - available
            .thenReturn(false); // Subsequent calls - not available
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenThrow(new RuntimeException("Service unavailable"));

        CommandSuggestion suggestion1 = commandProcessor.processInput("docker ps");
        assertNotNull(suggestion1);
        
        // Should fallback gracefully when service degrades
        CommandSuggestion suggestion2 = commandProcessor.processInput("docker ps");
        assertNotNull(suggestion2);
        assertFalse(suggestion2.isError());
    }

    @Test
    @DisplayName("Should integrate with real command metadata")
    void testRealCommandMetadataIntegration() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        // Test with real metadata from CommandMetadataRepository
        CommandSuggestion suggestion1 = structuralValidator.validateStructure("docker ps -a").orElse(null);
        assertNull(suggestion1, "Valid docker command should not need correction");

        CommandSuggestion suggestion2 = structuralValidator.validateStructure("docker sp -a").orElse(null);
        assertNotNull(suggestion2, "Invalid docker command should need correction");
        assertTrue(suggestion2.needsCorrection());
        assertTrue(suggestion2.getSuggestion().contains("ps"));
    }

    @Test
    @DisplayName("Should handle end-to-end smart command processing")
    void testEndToEndSmartCommandProcessing() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.suggestCommandsForTask("find large files"))
            .thenReturn("find . -type f -size +100M");

        CommandSuggestion suggestion = commandProcessor.processInput("sc 'find large files'");
        
        assertNotNull(suggestion);
        assertTrue(suggestion.isSmartCommand());
        assertEquals("find . -type f -size +100M", suggestion.getSuggestion());
        
        verify(commandHistoryRepository).save(any(CommandHistory.class));
    }

    @Test
    @DisplayName("Should handle mixed validation scenarios across services")
    void testMixedValidationScenarios() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any())).thenReturn("ps");

        // Test scenarios that involve multiple validation layers
        String[] mixedCommands = {
            "docker ps",           // Valid command
            "docker sp",           // Typo correction
            "git status",          // Valid git command
            "kubectl get pods",    // Valid kubectl command
            "unknowncommand"       // Unknown command
        };

        for (String command : mixedCommands) {
            CommandSuggestion suggestion = commandProcessor.processInput(command);
            assertNotNull(suggestion, "Command should not return null: " + command);
            
            if (command.equals("unknowncommand")) {
                // Unknown commands should be handled gracefully
                // With the mock setup, unknown commands may get corrections from Ollama
                assertTrue(suggestion.getType() == CommandSuggestion.SuggestionType.REGULAR ||
                          suggestion.getType() == CommandSuggestion.SuggestionType.CORRECTION ||
                          suggestion.isError());
            }
        }
    }

    @Test
    @DisplayName("Should maintain consistency across service interactions")
    void testServiceInteractionConsistency() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        String testCommand = "docker ps -a";
        
        // Test that all services handle the same command consistently
        CommandSuggestion processorResult = commandProcessor.processInput(testCommand);
        CommandSuggestion validatorResult = intelligentValidator.validateAndCorrect(testCommand);
        CommandSuggestion fallbackResult = fallbackValidator.validate(testCommand);
        
        assertNotNull(processorResult);
        assertNotNull(validatorResult);
        assertNotNull(fallbackResult);
        
        // All should agree that this is a valid command
        assertFalse(processorResult.needsCorrection());
        assertFalse(validatorResult.needsCorrection());
        assertFalse(fallbackResult.needsCorrection());
    }

    @Test
    @DisplayName("Should handle resource cleanup properly")
    void testResourceCleanup() throws InterruptedException {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any())).thenReturn("ps");

        // Process many commands and ensure resources are cleaned up
        for (int i = 0; i < 100; i++) {
            CommandSuggestion suggestion = commandProcessor.processInput("docker ps " + i);
            assertNotNull(suggestion);
        }

        // Force garbage collection
        System.gc();
        Thread.sleep(100);

        // Service should still be responsive
        CommandSuggestion finalSuggestion = commandProcessor.processInput("docker ps");
        assertNotNull(finalSuggestion);
    }

    @Test
    @DisplayName("Should handle timeout scenarios gracefully")
    void testTimeoutScenarios() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenAnswer(inv -> {
                // Simulate long-running operation
                Thread.sleep(6000); // Longer than typical timeout
                return "ps";
            });

        // Should handle timeout gracefully without hanging
        long startTime = System.currentTimeMillis();
        CommandSuggestion suggestion = commandProcessor.processInput("docker ps");
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(suggestion);
        assertTrue(duration < 10000, "Should timeout or complete quickly, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Should validate performance with large metadata sets")
    void testPerformanceWithLargeMetadataSets() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        // Test performance with structural validation using large metadata
        String[] commands = {
            "docker ps", "docker images", "docker run nginx", "docker stop container",
            "git status", "git add .", "git commit -m 'test'", "git push origin main",
            "kubectl get pods", "kubectl get services", "kubectl apply -f file.yaml",
            "npm install", "npm test", "npm run build", "npm start"
        };

        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 100; i++) {
            String command = commands[i % commands.length];
            CommandSuggestion suggestion = structuralValidator.validateStructure(command).orElse(null);
            // Valid commands should return null (no correction needed)
            assertNull(suggestion, "Valid command should not need correction: " + command);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 1000, "Should validate 100 commands quickly, took: " + duration + "ms");
    }
}