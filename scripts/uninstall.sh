#!/bin/bash

################################################################################
# Smart Commands Uninstallation Script
#
# This script uninstalls Smart Commands CLI tool with the following steps:
# 1. Stops and removes auto-start services
# 2. Stops Smart Commands server
# 3. Stops Ollama service (does NOT uninstall Ollama)
# 4. Removes installation directory
# 5. Cleans up shell integration from .zshrc
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Installation configuration
INSTALL_DIR="$HOME/.smart-commands"
SCRIPTS_DIR="$INSTALL_DIR/scripts"
LOGS_DIR="$INSTALL_DIR/logs"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Application configuration
APP_NAME="Smart Commands"
APP_VERSION="1.0.0"

################################################################################
# Utility Functions
################################################################################

print_header() {
    echo
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo
}

print_step() {
    echo -e "${BLUE}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${MAGENTA}ℹ $1${NC}"
}

# Check if command exists
command_exists() {
    command -v "$1" &> /dev/null
}

# Detect OS
detect_os() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "macos"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "linux"
    else
        echo "unknown"
    fi
}

# Ask for confirmation
confirm() {
    local message="$1"
    local default="${2:-n}"
    
    if [ "$default" = "y" ]; then
        read -p "$message (Y/n): " -n 1 -r
        echo
        [[ $REPLY =~ ^[Nn]$ ]] && return 1
    else
        read -p "$message (y/N): " -n 1 -r
        echo
        [[ ! $REPLY =~ ^[Yy]$ ]] && return 1
    fi
    return 0
}

################################################################################
# Uninstallation Steps
################################################################################

# Step 1: Stop Smart Commands Server
stop_smart_commands_server() {
    print_step "Stopping Smart Commands server..."

    if [ -f "$SCRIPTS_DIR/smart-commands-server.sh" ]; then
        "$SCRIPTS_DIR/smart-commands-server.sh" stop 2>/dev/null || true
        print_success "Smart Commands server stopped"
    else
        print_warning "Smart Commands server script not found"
    fi
}

# Step 2: Stop Ollama Service (only stop, don't uninstall)
stop_ollama() {
    print_step "Stopping Ollama service..."

    local os=$(detect_os)
    
    if command_exists ollama; then
        if [ "$os" = "macos" ]; then
            # Stop Ollama app on macOS
            pkill -f "ollama serve" 2>/dev/null || true
            # Also try to stop via launchctl if it was started that way
            launchctl stop com.ollama.ollama 2>/dev/null || true
        else
            # Stop Ollama service on Linux
            pkill -f "ollama serve" 2>/dev/null || true
            systemctl --user stop ollama.service 2>/dev/null || true
        fi
        print_success "Ollama service stopped"
        print_info "Note: Ollama application is NOT uninstalled, only stopped"
    else
        print_warning "Ollama not found"
    fi
}

# Step 3: Remove Auto-start Services
remove_autostart_services() {
    print_step "Removing auto-start services..."

    local os=$(detect_os)

    if [ "$os" = "macos" ]; then
        remove_macos_autostart
    elif [ "$os" = "linux" ]; then
        remove_linux_autostart
    else
        print_warning "Auto-start removal not supported for this OS"
    fi

    print_success "Auto-start services removed"
}

# Remove macOS auto-start services
remove_macos_autostart() {
    print_step "Removing macOS launchd services..."

    local launch_agents_dir="$HOME/Library/LaunchAgents"
    
    # Unload and remove Ollama launch agent
    if [ -f "$launch_agents_dir/com.ollama.ollama.plist" ]; then
        launchctl unload "$launch_agents_dir/com.ollama.ollama.plist" 2>/dev/null || true
        rm -f "$launch_agents_dir/com.ollama.ollama.plist"
        print_success "Removed Ollama launch agent"
    fi

    # Unload and remove Smart Commands launch agent
    if [ -f "$launch_agents_dir/com.smartcommands.server.plist" ]; then
        launchctl unload "$launch_agents_dir/com.smartcommands.server.plist" 2>/dev/null || true
        rm -f "$launch_agents_dir/com.smartcommands.server.plist"
        print_success "Removed Smart Commands launch agent"
    fi
}

# Remove Linux auto-start services
remove_linux_autostart() {
    print_step "Removing Linux systemd services..."

    local systemd_dir="$HOME/.config/systemd/user"
    
    # Stop and disable Ollama service
    if [ -f "$systemd_dir/ollama.service" ]; then
        systemctl --user stop ollama.service 2>/dev/null || true
        systemctl --user disable ollama.service 2>/dev/null || true
        rm -f "$systemd_dir/ollama.service"
        print_success "Removed Ollama systemd service"
    fi

    # Stop and disable Smart Commands service
    if [ -f "$systemd_dir/smart-commands.service" ]; then
        systemctl --user stop smart-commands.service 2>/dev/null || true
        systemctl --user disable smart-commands.service 2>/dev/null || true
        rm -f "$systemd_dir/smart-commands.service"
        print_success "Removed Smart Commands systemd service"
    fi

    # Reload systemd daemon
    systemctl --user daemon-reload 2>/dev/null || true
}

# Step 4: Clean up Shell Integration
cleanup_shell_integration() {
    print_step "Cleaning up shell integration..."

    local zshrc="$HOME/.zshrc"
    local integration_marker="# Smart Commands Integration"
    local end_marker="# End Smart Commands Integration"

    if [ -f "$zshrc" ]; then
        # Check if Smart Commands integration exists
        if grep -q "$integration_marker" "$zshrc" 2>/dev/null; then
            # Create backup
            cp "$zshrc" "$zshrc.backup.$(date +%Y%m%d_%H%M%S)"
            
            # Remove Smart Commands integration
            sed -i.bak "/$integration_marker/,/$end_marker/d" "$zshrc" 2>/dev/null || true
            rm -f "$zshrc.bak"
            
            print_success "Shell integration removed from .zshrc"
            print_info "Backup created: $zshrc.backup.$(date +%Y%m%d_%H%M%S)"
        else
            print_warning "No Smart Commands integration found in .zshrc"
        fi
    else
        print_warning ".zshrc file not found"
    fi
}

# Step 5: Remove Installation Directory
remove_installation_directory() {
    print_step "Removing installation directory..."

    if [ -d "$INSTALL_DIR" ]; then
        # Ask for confirmation before removing
        if confirm "Remove installation directory: $INSTALL_DIR ?"; then
            rm -rf "$INSTALL_DIR"
            print_success "Installation directory removed"
        else
            print_warning "Installation directory not removed"
            print_info "You can manually remove it later: rm -rf $INSTALL_DIR"
        fi
    else
        print_warning "Installation directory not found"
    fi
}

# Step 6: Display Uninstallation Summary
show_summary() {
    print_header "Uninstallation Complete!"

    echo -e "${GREEN}Smart Commands v$APP_VERSION has been successfully uninstalled!${NC}"
    echo
    echo -e "${YELLOW}What was removed:${NC}"
    echo -e "  ✓ Smart Commands server stopped"
    echo -e "  ✓ Auto-start services removed"
    echo -e "  ✓ Shell integration cleaned from .zshrc"
    echo -e "  ✓ Installation directory: $INSTALL_DIR"
    echo
    echo -e "${YELLOW}What was NOT removed:${NC}"
    echo -e "  ⚠ Ollama application (only stopped, not uninstalled)"
    echo -e "  ⚠ Downloaded Ollama models"
    echo -e "  ⚠ Java installation (if installed globally)"
    echo
    echo -e "${CYAN}To completely remove Ollama (optional):${NC}"
    echo -e "  macOS: ${BLUE}rm -rf /usr/local/bin/ollama${NC}"
    echo -e "  Linux: ${BLUE}sudo rm -rf /usr/local/bin/ollama${NC}"
    echo -e "  Remove models: ${BLUE}rm -rf ~/.ollama${NC}"
    echo
    echo -e "${GREEN}Thank you for using Smart Commands! 👋${NC}"
    echo
}

################################################################################
# Main Uninstallation Flow
################################################################################

main() {
    print_header "$APP_NAME Uninstaller v$APP_VERSION"

    echo -e "${CYAN}This script will uninstall Smart Commands from your system${NC}"
    echo -e "${CYAN}Installation directory: $INSTALL_DIR${NC}"
    echo
    echo -e "${YELLOW}Note: Ollama will be stopped but NOT uninstalled${NC}"
    echo

    # Ask for confirmation
    if ! confirm "Do you want to proceed with uninstallation?"; then
        echo -e "${YELLOW}Uninstallation cancelled${NC}"
        exit 0
    fi

    echo

    # Run uninstallation steps
    stop_smart_commands_server
    stop_ollama
    remove_autostart_services
    cleanup_shell_integration
    remove_installation_directory

    # Show summary
    show_summary
}

# Run main uninstallation
main "$@"