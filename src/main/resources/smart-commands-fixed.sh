#!/bin/bash

# Smart Commands Shell Integration - Fixed Version
# AI-powered command validation and correction using Ollama
# More conservative approach to avoid interfering with normal shell operation

# Configuration
SMART_COMMANDS_URL="http://localhost:17020"
OLLAMA_MODEL="qwen2.5-coder:3b"
OLLAMA_API="http://localhost:11434/api"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to check if Ollama is available
check_ollama() {
    if ! command -v ollama &> /dev/null; then
        return 1
    fi

    if ! ollama list | grep -q "$OLLAMA_MODEL" 2>/dev/null; then
        return 1
    fi

    return 0
}

# Function to check if a command is likely a typo of a common command
is_likely_typo() {
    local cmd="$1"
    
    # Only consider short commands (1-4 characters) as potential typos
    if [ ${#cmd} -gt 4 ] || [ ${#cmd} -lt 1 ]; then
        return 1
    fi
    
    # Only consider alphabetic commands
    if ! [[ "$cmd" =~ ^[a-z]+$ ]]; then
        return 1
    fi
    
    # Common commands that could have typos
    local common_commands=("ls" "cd" "pwd" "cat" "less" "more" "head" "tail" "grep" "find" "cp" "mv" "rm" "mkdir" "rmdir" "touch" "chmod" "chown" "ps" "kill" "top" "df" "du" "git" "docker" "npm" "yarn" "curl" "wget" "ssh" "scp" "rsync")
    
    for common_cmd in "${common_commands[@]}"; do
        # Simple Levenshtein distance approximation
        if [ ${#cmd} -eq ${#common_cmd} ] || [ $((${#cmd} - ${#common_cmd})) -eq 1 ] || [ $((${#common_cmd} - ${#cmd})) -eq 1 ]; then
            # Check if at least half the characters match in order
            local matches=0
            local shorter=${#cmd}
            if [ ${#common_cmd} -lt ${#cmd} ]; then
                shorter=${#common_cmd}
            fi
            
            # Simple character matching (not perfect but good enough for our use case)
            local i=0
            while [ $i -lt $shorter ]; do
                if [ "${cmd:$i:1}" = "${common_cmd:$i:1}" ]; then
                    matches=$((matches + 1))
                fi
                i=$((i + 1))
            done
            
            # If at least 50% match and length difference is <= 1, it's likely a typo
            if [ $matches -ge $((shorter / 2)) ]; then
                return 0
            fi
        fi
    done
    
    return 1
}

# Function to get command suggestion from Ollama
get_ollama_suggestion() {
    local command="$1"

    if ! check_ollama; then
        return 1
    fi

    # Be very conservative - only fix clear typos
    local prompt="Fix ONLY obvious typos in shell commands. If the command looks correct, return CORRECT.

Examples of typos to fix:
Input: docker sp -a
Fixed: docker ps -a

Input: lss -la
Fixed: ls -la

Input: git comit -m test
Fixed: git commit -m test

Input: greDp -r test file.txt
Fixed: grep -r test file.txt

Examples of correct commands (do NOT change):
Input: ls -la
Fixed: CORRECT

Input: docker ps -a
Fixed: CORRECT

Input: git status
Fixed: CORRECT

Now fix only if there's a clear typo:
Input: $command
Fixed:"

    # Call Ollama API
    local response
    response=$(curl -s -X POST "$OLLAMA_API/generate" \
        -H "Content-Type: application/json" \
        -d "{\"model\":\"$OLLAMA_MODEL\",\"prompt\":$(echo "$prompt" | jq -R -s .),\"stream\":false}" 2>/dev/null)
    local curl_exit=$?

    if [ $curl_exit -ne 0 ] || [ -z "$response" ]; then
        return 1
    fi

    # Extract the response
    local suggestion=$(printf '%s' "$response" | jq -r '.response' 2>/dev/null)

    if [ -z "$suggestion" ] || [ "$suggestion" = "null" ]; then
        return 1
    fi

    # Remove markdown code blocks and clean up
    suggestion=$(echo "$suggestion" | sed 's/^```.*$//' | sed 's/^```$//' | tr -d '\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

    # Extract the actual command - try multiple patterns
    if [[ "$suggestion" =~ 'Fixed:[[:space:]]*`?([^`]+)`?' ]]; then
        suggestion="${match[1]}"
    elif [[ "$suggestion" =~ 'Fixed:[[:space:]]*(.+)' ]]; then
        suggestion="${match[1]}"
        suggestion=$(echo "$suggestion" | sed 's/Corrected command:.*//' | sed 's/`.*//' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    fi

    # Clean up any remaining artifacts
    suggestion=$(echo "$suggestion" | sed 's/The fixed command is://' | sed 's/Corrected command://' | sed 's/`//g' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

    # If Ollama says it's correct, return empty
    if [ "$suggestion" = "CORRECT" ]; then
        return 1
    fi

    # Additional validation: don't suggest if it's too different from original
    if [ -n "$suggestion" ]; then
        local original_words=$(echo "$command" | wc -w)
        local suggestion_words=$(echo "$suggestion" | wc -w)
        
        # If word count differs by more than 2, it's probably not just a typo fix
        if [ $((original_words - suggestion_words)) -gt 2 ] || [ $((suggestion_words - original_words)) -gt 2 ]; then
            return 1
        fi
    fi

    if [ -z "$suggestion" ]; then
        return 1
    fi

    echo "$suggestion"
    return 0
}

# Function to display suggestion and get user confirmation
display_suggestion() {
    local original="$1"
    local suggestion="$2"

    echo
    echo -e "${CYAN}💡 Smart Commands Suggestion:${NC}"
    echo -e "${YELLOW}   Original:  $original${NC}"
    echo -e "${GREEN}   Suggested: $suggestion${NC}"
    echo

    # Copy to clipboard if possible
    if command -v pbcopy &> /dev/null; then
        echo "$suggestion" | pbcopy
        echo -e "${BLUE}📋 Command copied to clipboard!${NC}"
    elif command -v xclip &> /dev/null; then
        echo "$suggestion" | xclip -selection clipboard
        echo -e "${BLUE}📋 Command copied to clipboard!${NC}"
    fi

    echo -e "${YELLOW}Press Enter to execute, Ctrl+C to cancel${NC}"
    read -r response

    echo -e "${GREEN}🚀 Executing: $suggestion${NC}"
    eval "$suggestion"
}

# Wrapper function for validating commands - more conservative
validate_and_execute() {
    local cmd_name="$1"
    shift
    local full_cmd="$cmd_name $*"

    # Skip validation if no arguments or simple flags that don't need correction
    if [ $# -eq 0 ] || [[ "$*" =~ ^(--help|-h|--version|-v|--status|--list)$ ]]; then
        command "$cmd_name" "$@"
        return
    fi

    # Only validate if there are suspicious patterns that might indicate typos
    local has_typo_indicators=false
    
    # Check for common typo patterns
    if [[ "$full_cmd" =~ (sp|ps|lss|ls|comit|commit|greDp|grep) ]]; then
        has_typo_indicators=true
    fi
    
    # Only validate if we suspect there might be a typo
    if [ "$has_typo_indicators" = true ]; then
        local suggestion
        suggestion=$(get_ollama_suggestion "$full_cmd")
        local ollama_exit=$?

        if [ $ollama_exit -eq 0 ] && [ -n "$suggestion" ] && [ "$suggestion" != "$full_cmd" ]; then
            display_suggestion "$full_cmd" "$suggestion"
            return
        fi
    fi

    # No validation needed, execute original
    command "$cmd_name" "$@"
}

# Create wrapper functions for common commands - more selective
# Only wrap commands that commonly have typos in subcommands

docker() {
    # Only validate if it looks like there might be a typo in subcommands
    if [[ "$*" =~ (sp|ps|rum|run|buid|build|stop|start|logs|exec) ]]; then
        validate_and_execute docker "$@"
    else
        command docker "$@"
    fi
}

git() {
    # Only validate if it looks like there might be a typo in subcommands
    if [[ "$*" =~ (comit|commit|stauts|status|pus|push|pul|pull|clon|clone|checkout|branch|merge) ]]; then
        validate_and_execute git "$@"
    else
        command git "$@"
    fi
}

# Don't wrap kubectl, npm, cargo by default since they're less prone to simple typos
# Users can uncomment these if they want the validation:

# kubectl() {
#     validate_and_execute kubectl "$@"
# }

# npm() {
#     validate_and_execute npm "$@"
# }

# cargo() {
#     validate_and_execute cargo "$@"
# }

# Smart command function for natural language requests
smart_command() {
    local task="$1"

    if [ -z "$task" ]; then
        echo -e "${YELLOW}Usage: sc 'describe what you want to do'${NC}"
        echo -e "${CYAN}Example: sc 'find all java files in current directory'${NC}"
        return 1
    fi

    echo -e "${BLUE}🤖 Processing: $task${NC}"

    if check_ollama; then
        local prompt="Generate a Linux/macOS shell command for this task: $task

Rules:
- Respond with ONLY the command, no explanation
- Use common shell commands
- Make it safe and practical
- Consider the current shell context

Command:"

        local response=$(curl -s -X POST "$OLLAMA_API/generate" \
            -H "Content-Type: application/json" \
            -d "{\"model\":\"$OLLAMA_MODEL\",\"prompt\":$(echo "$prompt" | jq -R -s .),\"stream\":false}" 2>/dev/null)

        local suggestion=$(printf '%s' "$response" | jq -r '.response' 2>/dev/null | tr -d '\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

        if [ -n "$suggestion" ] && [ "$suggestion" != "null" ]; then
            display_suggestion "sc '$task'" "$suggestion"
            return
        fi
    fi

    echo -e "${RED}❌ Failed to generate command${NC}"
    echo -e "${YELLOW}Make sure Ollama is running with model: $OLLAMA_MODEL${NC}"
}

# Command not found handler - very conservative approach
command_not_found_handler() {
    local cmd="$1"
    shift
    local full_cmd="$cmd $*"

    # Only attempt correction for likely typos of common commands
    if is_likely_typo "$cmd"; then
        echo -e "${YELLOW}⚠ Command '$cmd' not found. Checking for possible typo...${NC}"
        
        # Try to get correction
        local suggestion
        suggestion=$(get_ollama_suggestion "$full_cmd")
        local ollama_exit=$?

        if [ $ollama_exit -eq 0 ] && [ -n "$suggestion" ] && [ "$suggestion" != "$full_cmd" ]; then
            display_suggestion "$full_cmd" "$suggestion"
            return 127
        fi
    fi

    # No correction attempted or failed, show standard error
    echo -e "${RED}zsh: command not found: $cmd${NC}" >&2
    return 127
}

# Setup aliases
alias sc='smart_command'

echo -e "${GREEN}✓ Smart Commands shell integration loaded (Fixed Version)${NC}"
echo -e "${BLUE}Commands wrapped: docker, git (conservative approach)${NC}"
echo -e "${CYAN}Type 'sc \"your task\"' for natural language commands${NC}"
echo -e "${YELLOW}Note: Command validation is now more conservative to avoid interference${NC}"