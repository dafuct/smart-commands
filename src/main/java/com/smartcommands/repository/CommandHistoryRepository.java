package com.smartcommands.repository;

import com.smartcommands.model.CommandHistory;
import com.smartcommands.model.CommandSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommandHistoryRepository extends JpaRepository<CommandHistory, Long> {
    
    /**
     * Find command history by session ID
     */
    List<CommandHistory> findBySessionIdOrderByTimestampDesc(String sessionId);
    
    /**
     * Find command history by suggestion type
     */
    List<CommandHistory> findBySuggestionTypeOrderByTimestampDesc(CommandSuggestion.SuggestionType suggestionType);
    
    /**
     * Find recent commands (last N days)
     */
    @Query("SELECT ch FROM CommandHistory ch WHERE ch.timestamp >= :since ORDER BY ch.timestamp DESC")
    List<CommandHistory> findRecentCommands(@Param("since") LocalDateTime since);
    
    /**
     * Find commands that were executed
     */
    List<CommandHistory> findByExecutedTrueOrderByTimestampDesc();
    
    /**
     * Find commands that were not executed (suggestions that were ignored)
     */
    List<CommandHistory> findByExecutedFalseOrderByTimestampDesc();
    
    /**
     * Search command history by original command or user input
     */
    @Query("SELECT ch FROM CommandHistory ch WHERE " +
           "LOWER(ch.originalCommand) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(ch.userInput) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY ch.timestamp DESC")
    List<CommandHistory> searchCommands(@Param("searchTerm") String searchTerm);
    
    /**
     * Get command statistics by type
     */
    @Query("SELECT ch.suggestionType, COUNT(ch) FROM CommandHistory ch " +
           "WHERE ch.timestamp >= :since " +
           "GROUP BY ch.suggestionType")
    List<Object[]> getCommandStatisticsByType(@Param("since") LocalDateTime since);
    
    /**
     * Get most frequently used commands
     */
    @Query("SELECT ch.originalCommand, COUNT(ch) FROM CommandHistory ch " +
           "WHERE ch.executed = true AND ch.timestamp >= :since " +
           "GROUP BY ch.originalCommand " +
           "ORDER BY COUNT(ch) DESC")
    List<Object[]> getMostUsedCommands(@Param("since") LocalDateTime since);
    
    /**
     * Clean old command history (older than specified date)
     */
    @Query("DELETE FROM CommandHistory ch WHERE ch.timestamp < :before")
    void deleteOldCommands(@Param("before") LocalDateTime before);
    
    /**
     * Count commands in the last N days
     */
    @Query("SELECT COUNT(ch) FROM CommandHistory ch WHERE ch.timestamp >= :since")
    long countCommandsSince(@Param("since") LocalDateTime since);
}
