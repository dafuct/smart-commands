package com.smartcommands.config;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class SmartCommandsConfig {

    @Bean
    public Terminal terminal() {
        try {
            return TerminalBuilder.builder()
                    .system(true)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize terminal", e);
        }
    }

    @Bean
    public LineReader lineReader() {
        return LineReaderBuilder.builder()
                .terminal(terminal())
                .build();
    }
}
