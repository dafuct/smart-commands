#!/bin/bash

################################################################################
# Smart Commands Uninstallation Script
#
# This script safely removes Smart Commands and all its components:
# 1. Stops all running services (Smart Commands server and Ollama)
# 2. Removes auto-start services (launchd on macOS, systemd on Linux)
# 3. Cleans up installation directories (~/.smart-commands)
# 4. Removes shell integration from .zshrc
# 5. Removes symlinks and PATH modifications
# 6. Provides verification and backup options
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

# Installation configuration (same as install script)
INSTALL_DIR="$HOME/.smart-commands"
BIN_DIR="$INSTALL_DIR/bin"
DATA_DIR="$INSTALL_DIR/data"
LOGS_DIR="$INSTALL_DIR/logs"
SCRIPTS_DIR="$INSTALL_DIR/scripts"
CONFIG_DIR="$INSTALL_DIR/config"
JDK_DIR="$INSTALL_DIR/jdk"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Default options
VERBOSE=false
REMOVE_OLLAMA=false
REMOVE_JAVA=false
NO_BACKUP=false
BACKUP_DIR="$HOME/smart-commands-backup-$(date +%Y%m%d-%H%M%S)"

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

print_verbose() {
    if [ "$VERBOSE" = true ]; then
        echo -e "${MAGENTA}  → $1${NC}"
    fi
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

# User confirmation
confirm() {
    local message="$1"
    local default="${2:-n}"
    
    if [ "$FORCE" = true ]; then
        return 0
    fi
    
    while true; do
        read -p "$message (y/N): " -n 1 -r
        echo
        case $REPLY in
            [Yy]* ) return 0;;
            [Nn]* ) return 1;;
            * ) echo "Please answer y or n.";;
        esac
    done
}

# Progress bar
show_progress() {
    local current="$1"
    local total="$2"
    local desc="$3"
    
    local percent=$((current * 100 / total))
    local filled=$((percent / 2))
    local empty=$((50 - filled))
    
    printf "\r${BLUE}▶ ${desc}: [${GREEN}"
    printf "%*s" $filled | tr ' ' '='
    printf "${NC}"
    printf "%*s" $empty | tr ' ' '-'
    printf "${NC}] ${percent}%%"
    
    if [ $current -eq $total ]; then
        echo
    fi
}

################################################################################
# Discovery Functions
################################################################################

discover_components() {
    print_step "Discovering Smart Commands components..."
    
    local components_found=0
    
    # Check installation directory
    if [ -d "$INSTALL_DIR" ]; then
        print_success "Found installation directory: $INSTALL_DIR"
        components_found=$((components_found + 1))
    else
        print_warning "Installation directory not found: $INSTALL_DIR"
    fi
    
    # Check running services
    if pgrep -f "smart-commands.jar" > /dev/null 2>&1; then
        print_success "Smart Commands server is running"
        components_found=$((components_found + 1))
    else
        print_info "Smart Commands server is not running"
    fi
    
    if pgrep -f "ollama" > /dev/null 2>&1; then
        print_success "Ollama service is running"
        components_found=$((components_found + 1))
    else
        print_info "Ollama service is not running"
    fi
    
    # Check shell integration
    if grep -q "Smart Commands" "$HOME/.zshrc" 2>/dev/null; then
        print_success "Shell integration found in .zshrc"
        components_found=$((components_found + 1))
    else
        print_info "No shell integration found in .zshrc"
    fi
    
    # Check auto-start services
    local os=$(detect_os)
    if [ "$os" = "macos" ]; then
        if [ -f "$HOME/Library/LaunchAgents/com.smartcommands.server.plist" ]; then
            print_success "macOS auto-start service found"
            components_found=$((components_found + 1))
        else
            print_info "No macOS auto-start service found"
        fi
    elif [ "$os" = "linux" ]; then
        if [ -f "$HOME/.config/systemd/user/smart-commands.service" ]; then
            print_success "Linux auto-start service found"
            components_found=$((components_found + 1))
        else
            print_info "No Linux auto-start service found"
        fi
    fi
    
    echo
    print_info "Total components found: $components_found"
    echo
    
    if [ $components_found -eq 0 ]; then
        print_warning "No Smart Commands components found on this system"
        if ! confirm "Continue with uninstall anyway?"; then
            print_info "Uninstallation cancelled"
            exit 0
        fi
    fi
}

################################################################################
# Service Management Functions
################################################################################

stop_smart_commands_server() {
    print_step "Stopping Smart Commands server..."
    
    if [ -f "$SCRIPTS_DIR/smart-commands-server.sh" ]; then
        "$SCRIPTS_DIR/smart-commands-server.sh" stop 2>/dev/null || true
        print_success "Smart Commands server stopped"
    else
        print_info "Server management script not found"
    fi
    
    # Force kill any remaining processes
    if pgrep -f "smart-commands.jar" > /dev/null 2>&1; then
        print_verbose "Force killing remaining Smart Commands processes"
        pkill -f "smart-commands.jar" || true
        sleep 2
    fi
}

stop_ollama_service() {
    if [ "$REMOVE_OLLAMA" = false ]; then
        print_info "Skipping Ollama service (use --remove-ollama to remove it)"
        return 0
    fi
    
    print_step "Stopping Ollama service..."
    
    # Stop Ollama process
    if pgrep -f "ollama" > /dev/null 2>&1; then
        pkill -f "ollama" || true
        sleep 3
        
        # Force kill if still running
        if pgrep -f "ollama" > /dev/null 2>&1; then
            pkill -9 -f "ollama" || true
        fi
        
        print_success "Ollama service stopped"
    else
        print_info "Ollama service was not running"
    fi
}

################################################################################
# Auto-start Removal Functions
################################################################################

remove_macos_autostart() {
    print_step "Removing macOS auto-start services..."
    
    local launch_agents_dir="$HOME/Library/LaunchAgents"
    local services_removed=0
    
    # Remove Smart Commands auto-start
    if [ -f "$launch_agents_dir/com.smartcommands.server.plist" ]; then
        print_verbose "Unloading Smart Commands launch agent"
        launchctl unload "$launch_agents_dir/com.smartcommands.server.plist" 2>/dev/null || true
        rm -f "$launch_agents_dir/com.smartcommands.server.plist"
        print_success "Smart Commands auto-start service removed"
        services_removed=$((services_removed + 1))
    fi
    
    # Remove Ollama auto-start (if requested)
    if [ "$REMOVE_OLLAMA" = true ] && [ -f "$launch_agents_dir/com.ollama.ollama.plist" ]; then
        print_verbose "Unloading Ollama launch agent"
        launchctl unload "$launch_agents_dir/com.ollama.ollama.plist" 2>/dev/null || true
        rm -f "$launch_agents_dir/com.ollama.ollama.plist"
        print_success "Ollama auto-start service removed"
        services_removed=$((services_removed + 1))
    fi
    
    if [ $services_removed -eq 0 ]; then
        print_info "No macOS auto-start services to remove"
    fi
}

remove_linux_autostart() {
    print_step "Removing Linux auto-start services..."
    
    local systemd_dir="$HOME/.config/systemd/user"
    local services_removed=0
    
    # Stop and disable Smart Commands service
    if [ -f "$systemd_dir/smart-commands.service" ]; then
        print_verbose "Stopping and disabling Smart Commands service"
        systemctl --user stop smart-commands.service 2>/dev/null || true
        systemctl --user disable smart-commands.service 2>/dev/null || true
        rm -f "$systemd_dir/smart-commands.service"
        print_success "Smart Commands auto-start service removed"
        services_removed=$((services_removed + 1))
    fi
    
    # Stop and disable Ollama service (if requested)
    if [ "$REMOVE_OLLAMA" = true ] && [ -f "$systemd_dir/ollama.service" ]; then
        print_verbose "Stopping and disabling Ollama service"
        systemctl --user stop ollama.service 2>/dev/null || true
        systemctl --user disable ollama.service 2>/dev/null || true
        rm -f "$systemd_dir/ollama.service"
        print_success "Ollama auto-start service removed"
        services_removed=$((services_removed + 1))
    fi
    
    # Reload systemd
    if [ $services_removed -gt 0 ]; then
        systemctl --user daemon-reload 2>/dev/null || true
    fi
    
    if [ $services_removed -eq 0 ]; then
        print_info "No Linux auto-start services to remove"
    fi
}

################################################################################
# Shell Integration Removal
################################################################################

remove_shell_integration() {
    print_step "Removing shell integration..."
    
    local zshrc="$HOME/.zshrc"
    local integration_marker="# Smart Commands Integration"
    
    if grep -q "$integration_marker" "$zshrc" 2>/dev/null; then
        # Create backup
        if [ "$NO_BACKUP" = false ]; then
            print_verbose "Backing up .zshrc to $BACKUP_DIR"
            mkdir -p "$BACKUP_DIR"
            cp "$zshrc" "$BACKUP_DIR/zshrc-backup"
        fi
        
        # Remove integration
        print_verbose "Removing Smart Commands integration from .zshrc"
        sed -i.bak "/$integration_marker/,/# End Smart Commands Integration/d" "$zshrc"
        rm -f "$zshrc.bak"
        
        print_success "Shell integration removed from .zshrc"
        print_info "Reload your shell: source ~/.zshrc"
    else
        print_info "No shell integration found in .zshrc"
    fi
}

################################################################################
# Directory Cleanup Functions
################################################################################

backup_important_data() {
    if [ "$NO_BACKUP" = true ]; then
        print_info "Skipping backup ( --no-backup specified)"
        return 0
    fi
    
    print_step "Creating backup of important data..."
    
    mkdir -p "$BACKUP_DIR"
    local backup_items=0
    
    # Backup configuration files
    if [ -f "$CONFIG_DIR/application.properties" ]; then
        cp "$CONFIG_DIR/application.properties" "$BACKUP_DIR/"
        backup_items=$((backup_items + 1))
    fi
    
    # Backup command history
    if [ -f "$DATA_DIR/smart-commands.mv.db" ]; then
        cp "$DATA_DIR/smart-commands.mv.db" "$BACKUP_DIR/"
        backup_items=$((backup_items + 1))
    fi
    
    # Backup shell integration
    if grep -q "Smart Commands" "$HOME/.zshrc" 2>/dev/null; then
        cp "$HOME/.zshrc" "$BACKUP_DIR/zshrc-backup"
        backup_items=$((backup_items + 1))
    fi
    
    if [ $backup_items -gt 0 ]; then
        print_success "Backup created at: $BACKUP_DIR"
        print_info "Items backed up: $backup_items"
    else
        print_info "No important data to backup"
    fi
}

remove_installation_directories() {
    print_step "Removing installation directories..."
    
    if [ -d "$INSTALL_DIR" ]; then
        print_verbose "Removing installation directory: $INSTALL_DIR"
        rm -rf "$INSTALL_DIR"
        print_success "Installation directory removed"
    else
        print_info "Installation directory not found"
    fi
}

remove_java_installation() {
    if [ "$REMOVE_JAVA" = false ]; then
        print_info "Keeping local Java installation (use --remove-java to remove it)"
        return 0
    fi
    
    print_step "Removing local Java installation..."
    
    if [ -d "$JDK_DIR" ]; then
        print_verbose "Removing Java directory: $JDK_DIR"
        rm -rf "$JDK_DIR"
        print_success "Local Java installation removed"
    else
        print_info "Local Java installation not found"
    fi
}

################################################################################
# Verification Functions
################################################################################

verify_removal() {
    print_step "Verifying complete removal..."
    
    local verification_passed=true
    local checks=0
    
    # Check installation directory
    if [ ! -d "$INSTALL_DIR" ]; then
        print_success "✓ Installation directory removed"
    else
        print_error "✗ Installation directory still exists"
        verification_passed=false
    fi
    checks=$((checks + 1))
    
    # Check running processes
    if ! pgrep -f "smart-commands.jar" > /dev/null 2>&1; then
        print_success "✓ Smart Commands server not running"
    else
        print_error "✗ Smart Commands server still running"
        verification_passed=false
    fi
    checks=$((checks + 1))
    
    # Check shell integration
    if ! grep -q "Smart Commands" "$HOME/.zshrc" 2>/dev/null; then
        print_success "✓ Shell integration removed"
    else
        print_error "✗ Shell integration still present"
        verification_passed=false
    fi
    checks=$((checks + 1))
    
    # Check auto-start services
    local os=$(detect_os)
    if [ "$os" = "macos" ]; then
        if [ ! -f "$HOME/Library/LaunchAgents/com.smartcommands.server.plist" ]; then
            print_success "✓ macOS auto-start service removed"
        else
            print_error "✗ macOS auto-start service still present"
            verification_passed=false
        fi
    elif [ "$os" = "linux" ]; then
        if [ ! -f "$HOME/.config/systemd/user/smart-commands.service" ]; then
            print_success "✓ Linux auto-start service removed"
        else
            print_error "✗ Linux auto-start service still present"
            verification_passed=false
        fi
    fi
    checks=$((checks + 1))
    
    echo
    if [ "$verification_passed" = true ]; then
        print_success "🎉 All verification checks passed!"
        print_info "Smart Commands has been completely removed"
    else
        print_warning "⚠ Some verification checks failed"
        print_info "You may need to manually remove remaining components"
    fi
    
    # Show backup location
    if [ "$NO_BACKUP" = false ] && [ -d "$BACKUP_DIR" ]; then
        echo
        print_info "💾 Backup available at: $BACKUP_DIR"
        print_info "Restore with: cp -r $BACKUP_DIR/* ~/"
    fi
}

################################################################################
# Help and Usage
################################################################################

show_help() {
    cat << EOF
${CYAN}Smart Commands Uninstaller v1.0.0${NC}

${YELLOW}USAGE:${NC}
    $0 [OPTIONS]

${YELLOW}OPTIONS:${NC}
    ${BLUE}--help${NC}           Show this help message
    ${BLUE}--verbose${NC}        Enable detailed output
    ${BLUE}--remove-ollama${NC}  Also remove Ollama service and model
    ${BLUE}--remove-java${NC}    Remove local Java installation
    ${BLUE}--no-backup${NC}      Skip creating backup of important data
    ${BLUE}--backup-dir DIR${NC}  Specify custom backup directory

${YELLOW}EXAMPLES:${NC}
    $0                                    # Standard uninstall
    $0 --verbose --remove-ollama           # Verbose with Ollama removal
    $0 --remove-java --no-backup           # Remove Java without backup
    $0 --backup-dir /tmp/my-backup         # Custom backup location

${YELLOW}NOTE:${NC}
    - By default, only Smart Commands components are removed
    - Use --remove-ollama to also remove Ollama
    - Use --remove-java to remove the locally installed Java
    - Backups are created automatically unless --no-backup is used

EOF
}

################################################################################
# Main Uninstallation Flow
################################################################################

main() {
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                show_help
                exit 0
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            --remove-ollama)
                REMOVE_OLLAMA=true
                shift
                ;;
            --remove-java)
                REMOVE_JAVA=true
                shift
                ;;
            --no-backup)
                NO_BACKUP=true
                shift
                ;;
            --backup-dir)
                shift
                BACKUP_DIR="$1"
                shift
                ;;
            *)
                print_error "Unknown option: $1"
                echo "Use --help for usage information"
                exit 1
                ;;
        esac
    done
    
    print_header "Smart Commands Uninstaller v1.0.0"
    
    echo -e "${CYAN}This script will safely remove Smart Commands from your system${NC}"
    echo -e "${CYAN}Backup location: $BACKUP_DIR${NC}"
    echo
    
    # Show what will be removed
    echo -e "${YELLOW}Components to be removed:${NC}"
    echo -e "  • Smart Commands installation directory"
    echo -e "  • Shell integration from .zshrc"
    echo -e "  • Auto-start services"
    if [ "$REMOVE_OLLAMA" = true ]; then
        echo -e "  • Ollama service and auto-start"
    fi
    if [ "$REMOVE_JAVA" = true ]; then
        echo -e "  • Local Java installation"
    fi
    echo
    
    # User confirmation
    if ! confirm "Do you want to proceed with uninstallation?"; then
        print_info "Uninstallation cancelled"
        exit 0
    fi
    
    # Execute uninstallation steps
    discover_components
    backup_important_data
    stop_smart_commands_server
    stop_ollama_service
    
    # Remove auto-start services based on OS
    local os=$(detect_os)
    if [ "$os" = "macos" ]; then
        remove_macos_autostart
    elif [ "$os" = "linux" ]; then
        remove_linux_autostart
    fi
    
    remove_shell_integration
    remove_installation_directories
    remove_java_installation
    
    # Final verification
    verify_removal
    
    echo
    print_success "🎉 Uninstallation completed successfully!"
    print_info "Please restart your terminal to apply changes"
}

# Run main uninstallation
main "$@"