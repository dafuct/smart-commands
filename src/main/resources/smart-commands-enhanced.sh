#!/bin/bash

# Smart Commands Shell Integration
# AI-powered command validation and correction using Ollama

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

# Function to get command suggestion from Ollama
get_ollama_suggestion() {
    local command="$1"

    if ! check_ollama; then
        return 1
    fi

    # Use simple few-shot learning with clear patterns
    local prompt="Fix typos in shell commands.

Example 1:
Input: docker sp -a
Fixed: docker ps -a

Example 2:
Input: lss -la
Fixed: ls -la

Example 3:
Input: git comit -m test
Fixed: git commit -m test

Example 4:
Input: greDp -r test file.txt
Fixed: grep -r test file.txt

Example 5:
Input: ls -la
Fixed: CORRECT

Now fix:
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

    # Remove markdown code blocks
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

# Wrapper function for validating commands
validate_and_execute() {
    local cmd_name="$1"
    shift
    local full_cmd="$cmd_name $*"

    # Get suggestion from Ollama
    local suggestion
    suggestion=$(get_ollama_suggestion "$full_cmd")
    local ollama_exit=$?

    if [ $ollama_exit -eq 0 ] && [ -n "$suggestion" ] && [ "$suggestion" != "$full_cmd" ]; then
        display_suggestion "$full_cmd" "$suggestion"
    else
        # No correction needed, execute original
        command "$cmd_name" "$@"
    fi
}

# Create wrapper functions for common commands
# These intercept the commands BEFORE execution

docker() {
    validate_and_execute docker "$@"
}

git() {
    validate_and_execute git "$@"
}

kubectl() {
    validate_and_execute kubectl "$@"
}

npm() {
    validate_and_execute npm "$@"
}

cargo() {
    validate_and_execute cargo "$@"
}

# Add more command wrappers as needed:
# yarn() { validate_and_execute yarn "$@"; }
# pnpm() { validate_and_execute pnpm "$@"; }
# terraform() { validate_and_execute terraform "$@"; }
# aws() { validate_and_execute aws "$@"; }

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

        local suggestion=$(printf '%s' "$response" | jq -r '.response' 2>/dev/null | tr -d '\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sed 's/^```bash//;s/```$//' | sed 's/^```sh//;s/```$//' | sed 's/^```//;s/```$//' | sed 's/^`//;s/`$//' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sed 's/^Fixed://;s/^[[:space:]]*//')

        if [ -n "$suggestion" ] && [ "$suggestion" != "null" ]; then
            display_suggestion "sc '$task'" "$suggestion"
            return
        fi
    fi

    echo -e "${RED}❌ Failed to generate command${NC}"
    echo -e "${YELLOW}Make sure Ollama is running with model: $OLLAMA_MODEL${NC}"
}

# Command not found handler - catches typos in base commands
command_not_found_handler() {
    local cmd="$1"
    shift
    local full_cmd="$cmd $*"

    # Try to get correction
    local suggestion
    suggestion=$(get_ollama_suggestion "$full_cmd")
    local ollama_exit=$?

    if [ $ollama_exit -eq 0 ] && [ -n "$suggestion" ] && [ "$suggestion" != "$full_cmd" ]; then
        display_suggestion "$full_cmd" "$suggestion"
        return 127
    fi

    # No suggestion found, show standard error
    echo -e "${RED}zsh: command not found: $cmd${NC}" >&2
    return 127
}

# Setup aliases
alias sc='smart_command'

echo -e "${GREEN}✓ Smart Commands shell integration loaded${NC}"
echo -e "${BLUE}Commands wrapped: docker, git, kubectl, npm, cargo${NC}"
echo -e "${CYAN}Type 'sc \"your task\"' for natural language commands${NC}"
