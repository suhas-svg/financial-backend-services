#!/usr/bin/env node

const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

// Determine the platform and script to use
const isWindows = process.platform === 'win32';
const scriptName = isWindows ? 'setup-e2e-infrastructure.ps1' : 'setup-e2e-infrastructure.sh';
const scriptPath = path.join(__dirname, scriptName);

// Get command from arguments
const command = process.argv[2];

if (!command) {
    console.error('Usage: node run-e2e-infrastructure.js <command>');
    console.error('Commands: start, stop, restart, status, logs, clean, validate, help');
    process.exit(1);
}

// Check if script exists
if (!fs.existsSync(scriptPath)) {
    console.error(`Script not found: ${scriptPath}`);
    process.exit(1);
}

// Prepare command arguments
let cmd, args;

if (isWindows) {
    cmd = 'powershell';
    args = ['-ExecutionPolicy', 'Bypass', '-File', scriptPath];
    
    // Map commands to PowerShell parameters
    switch (command) {
        case 'start':
            args.push('-Start');
            break;
        case 'stop':
            args.push('-Stop');
            break;
        case 'restart':
            args.push('-Restart');
            break;
        case 'status':
            args.push('-Status');
            break;
        case 'logs':
            args.push('-Logs');
            break;
        case 'clean':
            args.push('-Clean');
            break;
        case 'validate':
            args.push('-Validate');
            break;
        case 'help':
            // Just run the script without parameters to show help
            break;
        default:
            console.error(`Unknown command: ${command}`);
            process.exit(1);
    }
} else {
    cmd = 'bash';
    args = [scriptPath, command];
}

// Execute the script
console.log(`Running E2E infrastructure command: ${command}`);
console.log(`Executing: ${cmd} ${args.join(' ')}`);

const child = spawn(cmd, args, {
    stdio: 'inherit',
    cwd: __dirname
});

child.on('error', (error) => {
    console.error(`Failed to execute script: ${error.message}`);
    process.exit(1);
});

child.on('close', (code) => {
    if (code !== 0) {
        console.error(`Script exited with code ${code}`);
        process.exit(code);
    }
    console.log(`E2E infrastructure command '${command}' completed successfully`);
});