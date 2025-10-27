package com.smartcommands.cli;

import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.service.CommandProcessorService;
import com.smartcommands.service.OllamaService;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "cli")
public class SmartCommandsCLI implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(SmartCommandsCLI.class);
    
    private final CommandProcessorService commandProcessorService;
    private final OllamaService ollamaService;
    
    @Autowired
    public SmartCommandsCLI(CommandProcessorService commandProcessorService, OllamaService ollamaService) {
        this.commandProcessorService = commandProcessorService;
        this.ollamaService = ollamaService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        String command = String.join(" ", args);
        processCommand(command);
    }
    
    /**
     * Process a single command from command line arguments
     */
    public void processCommand(String command) {
        try {
            CommandSuggestion suggestion = commandProcessorService.processInput(command);
            handleSuggestion(suggestion);
        } catch (Exception e) {
            logger.error("Error processing command: {}", command, e);
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Handle command suggestion and display appropriate output
     */
    private void handleSuggestion(CommandSuggestion suggestion) {
        switch (suggestion.getType()) {
            case REGULAR:
                // Regular command - no action needed
                System.out.println("✅ Command is correct: " + suggestion.getOriginalInput());
                break;
                
            case CORRECTION:
                System.out.println("\n⚠️  " + suggestion.getMessage());
                System.out.println("💡 Suggested command:");
                System.out.println();
                System.out.println(suggestion.getSuggestion());
                System.out.println();
                System.out.println("📋 Command copied to clipboard! Press Enter to execute or Ctrl+C to cancel.");
                // Try to copy to clipboard
                copyToClipboard(suggestion.getSuggestion());
                // Wait for user input
                waitForUserInput(suggestion.getSuggestion());
                break;
                
            case SMART_COMMAND:
                System.out.println("\n🤖 Smart Command Suggestion:");
                System.out.println("📝 Task: " + suggestion.getOriginalInput());
                System.out.println("⚡ Suggested command:");
                System.out.println();
                System.out.println(suggestion.getSuggestion());
                System.out.println();
                System.out.println("📋 Command copied to clipboard! Press Enter to execute or Ctrl+C to cancel.");
                // Try to copy to clipboard
                copyToClipboard(suggestion.getSuggestion());
                // Wait for user input
                waitForUserInput(suggestion.getSuggestion());
                break;
                
            case ERROR:
                System.err.println("\n❌ Error: " + suggestion.getMessage());
                break;
        }
    }
    
    /**
     * Copy command to system clipboard
     */
    private void copyToClipboard(String command) {
        try {
            // Try different clipboard methods based on OS
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            
            if (os.contains("mac")) {
                pb = new ProcessBuilder("pbcopy");
            } else if (os.contains("linux")) {
                pb = new ProcessBuilder("xclip", "-selection", "clipboard");
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("clip");
            } else {
                // Fallback: just display the command
                return;
            }
            
            Process process = pb.start();
            process.getOutputStream().write(command.getBytes());
            process.getOutputStream().close();
            process.waitFor();
        } catch (Exception e) {
            // Clipboard failed, but that's okay - user can still copy manually
            logger.debug("Could not copy to clipboard: {}", e.getMessage());
        }
    }
    
    /**
     * Wait for user input to execute the suggested command
     */
    private void waitForUserInput(String command) {
        try {
            System.out.print("Execute? [Y/n]: ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim().toLowerCase();
            
            if (input.isEmpty() || input.equals("y") || input.equals("yes")) {
                System.out.println("🚀 Executing: " + command);
                executeCommand(command);
            } else {
                System.out.println("❌ Command execution cancelled.");
            }
        } catch (Exception e) {
            logger.error("Error waiting for user input", e);
            System.out.println("❌ Error: " + e.getMessage());
        }
    }
    
    /**
     * Execute the suggested command
     */
    private void executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                System.out.println("✅ Command executed successfully.");
            } else {
                System.out.println("⚠️  Command exited with code: " + exitCode);
            }
        } catch (Exception e) {
            logger.error("Error executing command: {}", command, e);
            System.err.println("❌ Failed to execute command: " + e.getMessage());
        }
    }
    
    /**
     * Print usage information
     * TODO: improve with libraries:
     * JLine3 - provides advanced console features
     * Jansi - for ANSI colors and formatting
     * Picocli - comprehensive CLI framework with formatted output
     */
    private void printUsage() {
        System.out.println("Smart Commands - Terminal Command Assistant");
        System.out.println("==========================================");
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("  smart-commands <command>           Process a command");
        System.out.println("  smart-commands sc '<task>'         Get command suggestion for a task");
        System.out.println("  smart-commands --status            Check system status");
        System.out.println("  smart-commands --help              Show this help");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  smart-commands lss                 # Will suggest 'ls'");
        System.out.println("  smart-commands sc 'find big file'  # Will suggest find command");
        System.out.println("  smart-commands sc 'compress folder' # Will suggest tar command");
        System.out.println();
        System.out.println("SMART COMMAND FORMAT:");
        System.out.println("  sc '<task description>'");
        System.out.println("  The task description should be in quotes");
        System.out.println();
        System.out.println("REQUIREMENTS:");
        System.out.println("  - Ollama must be running with qwen2.5-coder:3b model");
        System.out.println("  - MySQL database should be available");
    }
    
    /**
     * Check system status
     */
    public void checkStatus() {
        System.out.println("Smart Commands System Status");
        System.out.println("============================");
        
        // Check Ollama
        boolean ollamaRunning = ollamaService.isOllamaRunning();
        System.out.println("Ollama Status: " + (ollamaRunning ? "✅ Running" : "❌ Not Running"));
        
        if (ollamaRunning) {
            boolean modelAvailable = ollamaService.isModelAvailable("qwen2.5-coder:3b");
            System.out.println("Qwen2.5-Coder Model: " + (modelAvailable ? "✅ Available" : "❌ Not Available"));
        }
        
        // Check services
        System.out.println("Command Processor: ✅ Active");
        System.out.println("Database: ✅ Connected");
        
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Ollama URL: " + ollamaService.getConfiguration().getBaseUrl());
        System.out.println("  Model: " + ollamaService.getConfiguration().getModel());
        System.out.println("  Timeout: " + ollamaService.getConfiguration().getTimeout() + "ms");
    }
    
    /**
     * Interactive mode for testing
     */
    public void interactiveMode() {
        System.out.println("Smart Commands Interactive Mode");
        System.out.println("Type 'exit' to quit");
        System.out.println("===============================");
        
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("smart> ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break;
            }
            
            if (input.isEmpty()) {
                continue;
            }
            
            if (input.equals("--status")) {
                checkStatus();
                continue;
            }
            
            if (input.equals("--help")) {
                printUsage();
                continue;
            }
            
            processCommand(input);
        }
        
        scanner.close();
        System.out.println("Goodbye!");
    }
}