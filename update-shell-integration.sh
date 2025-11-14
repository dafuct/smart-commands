#!/bin/bash

# Update Shell Integration Script
# Replaces the existing shell integration with the fixed version

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
INSTALL_DIR="$HOME/.smart-commands"
SCRIPTS_DIR="$INSTALL_DIR/scripts"
PROJECT_DIR="/Users/makar/personal/smart-commands"

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Update shell integration
update_shell_integration() {
    echo -e "${BLUE}Updating Smart Commands shell integration...${NC}"

    # Check if installation directory exists
    if [ ! -d "$INSTALL_DIR" ]; then
        print_error "Smart Commands installation not found at $INSTALL_DIR"
        print_info "Please run the installation script first: ./scripts/install.sh"
        exit 1
    fi

    # Create scripts directory if it doesn't exist
    mkdir -p "$SCRIPTS_DIR"

    # Copy the fixed shell script
    if [ -f "$PROJECT_DIR/src/main/resources/smart-commands-enhanced.sh" ]; then
        cp "$PROJECT_DIR/src/main/resources/smart-commands-enhanced.sh" "$SCRIPTS_DIR/smart-commands-shell.sh"
        chmod +x "$SCRIPTS_DIR/smart-commands-shell.sh"
        print_success "Shell integration script updated"
    else
        print_error "Shell integration template not found"
        exit 1
    fi

    # Update .zshrc
    local zshrc="$HOME/.zshrc"
    local integration_marker="# Smart Commands Integration"

    print_info "Updating .zshrc integration..."

    # Remove old integration if it exists
    if grep -q "$integration_marker" "$zshrc" 2>/dev/null; then
        sed -i.bak "/$integration_marker/,/# End Smart Commands Integration/d" "$zshrc"
        print_success "Removed old integration from .zshrc"
    fi

    # Add new integration
    cat >> "$zshrc" << EOF

# Smart Commands Integration
# Source the Smart Commands shell integration
if [ -f "$SCRIPTS_DIR/smart-commands-shell.sh" ]; then
    source "$SCRIPTS_DIR/smart-commands-shell.sh"
fi

# Optional: Auto-start server on shell startup (uncomment to enable)
# if [ -f "$SCRIPTS_DIR/smart-commands-server.sh" ]; then
#     "$SCRIPTS_DIR/smart-commands-server.sh" start > /dev/null 2>&1
# fi

# End Smart Commands Integration
EOF

    print_success ".zshrc integration updated"

    print_info "Shell integration has been updated with the following improvements:"
    echo "  • Robust Ollama connectivity checking with timeout handling"
    echo "  • Graceful fallback suggestions when AI is unavailable"
    echo "  • User-friendly error messages with installation instructions"
    echo "  • Enhanced typo detection and correction patterns"
    echo "  • Improved timeout handling for all API calls"
    echo "  • Comprehensive offline functionality with basic suggestions"
    echo "  • Ultra-conservative approach for existing commands (no interference)"
    echo "  • Copies corrected commands to clipboard for convenience"
    echo
    print_info "To activate the updated integration, run: source ~/.zshrc"
    print_info "Or open a new terminal session."
}

# Main execution
main() {
    echo "Smart Commands Shell Integration Updater"
    echo "======================================="
    echo

    update_shell_integration

    echo
    print_success "Update completed successfully!"
    echo -e "${BLUE}The shell integration now provides helpful AI suggestions for unknown commands while remaining conservative with existing ones${NC}"
}

main "$@"