package com.smartcommands.cli;

import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.service.CommandProcessorService;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "cli")
public class SmartCommandsCLI implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(SmartCommandsCLI.class);

    private final CommandProcessorService commandProcessorService;
    private final Terminal terminal;
    private final LineReader lineReader;

    @Autowired
    public SmartCommandsCLI(CommandProcessorService commandProcessorService,
                            Terminal terminal, LineReader lineReader) {
        this.commandProcessorService = commandProcessorService;
        this.terminal = terminal;
        this.lineReader = lineReader;
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

    public void processCommand(String command) {
        try {
            CommandSuggestion suggestion = commandProcessorService.processInput(command);
            handleSuggestion(suggestion);
        } catch (Exception e) {
            logger.error("Error processing command: {}", command, e);
            terminal.writer().println("Error: " + e.getMessage());
            terminal.flush();
        }
    }

    private void handleSuggestion(CommandSuggestion suggestion) {
        switch (suggestion.getType()) {
            case REGULAR:
                terminal.writer().println("✅ Command is correct: " + suggestion.getOriginalInput());
                terminal.flush();
                break;

            case CORRECTION:
                String messageCorrection = """
                        \n⚠️  %s
                        💡 Suggested correction:
                        
                        %s
                        
                        📋 Command copied to clipboard! Press Enter to execute or Ctrl+C to cancel.
                        """.formatted(suggestion.getMessage(), suggestion.getSuggestion());
                terminal.writer().println(messageCorrection);
                terminal.flush();
                copyToClipboard(suggestion.getSuggestion());
                waitForUserInput(suggestion.getSuggestion());
                break;

            case SMART_COMMAND:
                String messageSmartCommand = """
                        \n🤖 Smart Command Generated!
                        📝 Task: %s
                        ⚡ Suggested command:
                        
                        %s
                        
                        📋 Command copied to clipboard! Press Enter to execute or Ctrl+C to cancel.
                        """.formatted(suggestion.getOriginalInput(), suggestion.getSuggestion());

                terminal.writer().println(messageSmartCommand);
                terminal.flush();
                copyToClipboard(suggestion.getSuggestion());
                waitForUserInput(suggestion.getSuggestion());
                break;

            case ERROR:
                terminal.writer().println("\n❌ Error: " + suggestion.getMessage());
                terminal.flush();
                break;
        }
    }

    private void copyToClipboard(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("mac")) {
                pb = new ProcessBuilder("pbcopy");
            } else if (os.contains("linux")) {
                pb = new ProcessBuilder("xclip", "-selection", "clipboard");
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("clip");
            } else {
                return;
            }

            Process process = pb.start();
            process.getOutputStream().write(command.getBytes());
            process.getOutputStream().close();
            process.waitFor();
        } catch (Exception e) {
            logger.error("Could not copy to clipboard: {}", e.getMessage());
        }
    }

    private void waitForUserInput(String command) {
        try {
            String input = lineReader.readLine("Execute? [Y/n]: ");

            if (input.isEmpty() || input.equals("y") || input.equals("yes")) {
                terminal.writer().println("🚀 Executing: " + command);
                terminal.flush();
                executeCommand(command);
            } else {
                terminal.writer().println("❌ Command execution cancelled.");
                terminal.flush();
            }
        } catch (Exception e) {
            logger.error("Error waiting for user input", e);
            terminal.writer().println("❌ Error: " + e.getMessage());
            terminal.flush();
        }
    }

    private void executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                terminal.writer().println("✅ Command executed successfully.");
            } else {
                terminal.writer().println("⚠️  Command exited with code: " + exitCode);
            }
            terminal.flush();
        } catch (Exception e) {
            logger.error("Error executing command: {}", command, e);
            terminal.writer().println("❌ Failed to execute command: " + e.getMessage());
            terminal.flush();
        }
    }

    private void printUsage() {
        String helpText = """
                Smart Commands - Terminal Command Assistant
                ==========================================
                
                USAGE:
                  smart-commands <command>           Process a command
                  smart-commands sc '<task>'         Get command suggestion for a task
                  smart-commands --status            Check system status
                  smart-commands --help              Show this help
                
                EXAMPLES:
                  smart-commands lss                 # Will suggest 'ls'
                  smart-commands sc 'find big file'  # Will suggest find command
                  smart-commands sc 'compress folder' # Will suggest tar command
                
                SMART COMMAND FORMAT:
                  sc '<task description>'
                  The task description should be in quotes
                """;

        terminal.writer().println(helpText);
        terminal.flush();
    }
}