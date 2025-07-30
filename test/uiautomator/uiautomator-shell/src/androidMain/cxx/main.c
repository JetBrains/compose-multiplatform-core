/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <android/log.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>
#include <limits.h>
#include <stdint.h>

// Tag for Android logs
#define LOG_TAG "NativeShellProcess"

int verboselogs = 0;

#define LOGI(...) if (verboselogs == 1) { \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); \
};
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

int createSocket(uint16_t port) {

    // Create socket address
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(struct sockaddr_in));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = INADDR_ANY;

    // Create socket
    int fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd == -1) {
        LOGE("socket(AF_INET, SOCK_STREAM) failed: %s", strerror(errno));
        return -1;
    }

    // Allow immediate reuse of the port
    int reuse = 1;
    if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse)) == -1) {
        LOGE("setsockopt(SO_REUSEADDR) failed: %s", strerror(errno));
        close(fd);
        return -1;
    }

    // Bind the socket to the address and port
    if (bind(fd, (struct sockaddr *) &addr, sizeof(addr)) == -1) {
        LOGE("bind() to port %u failed: %s", port, strerror(errno));
        close(fd);
        return -1;
    }

    // Start listening for incoming connections
    if (listen(fd, 1) == -1) {
        LOGE("listen() on port %u failed: %s", port, strerror(errno));
        close(fd);
        return -1;
    }

    LOGI("TCP server listening on port %u", port);
    return fd;
}

int acceptConnection(int socketFd) {
    struct sockaddr_in clientAddr;
    socklen_t clientAddrLen = sizeof(clientAddr);

    // Accept an incoming connection
    int clientFd = accept(socketFd, (struct sockaddr *) &clientAddr, &clientAddrLen);
    if (clientFd == -1) {
        LOGE("accept() failed: %s", strerror(errno));
        return -1;
    }

    // Log client connection information
    char clientIpStr[INET_ADDRSTRLEN];
    if (inet_ntop(AF_INET, &clientAddr.sin_addr, clientIpStr, sizeof(clientIpStr)) != NULL) {
        LOGI("Accepted TCP connection from %s:%u on fd: %d",
             clientIpStr,
             ntohs(clientAddr.sin_port),
             clientFd);
    } else {
        LOGI("Accepted TCP connection (could not get client IP) on fd: %d", clientFd);
    }

    return clientFd;
}

int parsePortArg(const char *arg, uint16_t *port_out) {
    char *endptr;
    unsigned long parsed_port;

    errno = 0; // Reset errno before strtoul
    parsed_port = strtoul(arg, &endptr, 10);

    // Check for conversion errors or invalid input
    if (errno != 0 || *endptr != '\0' || parsed_port > UINT16_MAX) {
        LOGE("Invalid port number provided: %s", arg);
        return -1;
    }
    *port_out = (uint16_t)parsed_port;
    return 0;
}

int main(int argc, char *argv[]) {

    // Check the number of arguments
    if (argc != 6) {
        LOGE("Usage: %s <verbose_logs: 0 or 1> <stdin_socket_port> "
            "<stdout_socket_port> <stderr_socket_port> <command>",
             argv[0]);
        return 1;
    }

    verboselogs = atoi(argv[1]);

    // Get the socket ports and command from the arguments
    uint16_t stdinSocketPort, stdoutSocketPort, stderrSocketPort;
    if (parsePortArg(argv[2], &stdinSocketPort) != 0) return 1;
    if (parsePortArg(argv[3], &stdoutSocketPort) != 0) return 1;
    if (parsePortArg(argv[4], &stderrSocketPort) != 0) return 1;

    // Use char* directly, no std::string
    const char *command = argv[5];

    LOGI("Starting native shell with stdin port: "
        "%u, stdout port: %u, stderr port: %u, command: %s",
         stdinSocketPort,
         stdoutSocketPort,
         stderrSocketPort,
         command); // Use command directly

    // Create the sockets, checking for errors after each step
    int stdinSocketFd = createSocket(stdinSocketPort);
    if (stdinSocketFd == -1) {
        return 1;
    }

    int stdoutSocketFd = createSocket(stdoutSocketPort);
    if (stdoutSocketFd == -1) {
        close(stdinSocketFd);
        return 1;
    }

    int stderrSocketFd = createSocket(stderrSocketPort);
    if (stderrSocketFd == -1) {
        close(stdinSocketFd);
        close(stdoutSocketFd);
        return 1;
    }

    // Await and accept connections, checking for errors
    int clientStdinFd = acceptConnection(stdinSocketFd);
    if (clientStdinFd == -1) {
        close(stderrSocketFd);
        close(stdoutSocketFd);
        close(stdinSocketFd);
        return 1;
    }

    int clientStdoutFd = acceptConnection(stdoutSocketFd);
    if (clientStdoutFd == -1) {
        close(clientStdinFd);
        close(stderrSocketFd);
        close(stdoutSocketFd);
        close(stdinSocketFd);
        return 1;
    }

    int clientStderrFd = acceptConnection(stderrSocketFd);
    if (clientStderrFd == -1) {
        close(clientStdoutFd);
        close(clientStdinFd);
        close(stderrSocketFd);
        close(stdoutSocketFd);
        close(stdinSocketFd);
        return 1;
    }

    // Fork the process
    pid_t childPid = fork();

    // The process forking failed, clean up resources
    if (childPid == -1) {
        LOGE("fork() failed: %s", strerror(errno));
        close(clientStderrFd);
        close(clientStdoutFd);
        close(clientStdinFd);
        close(stderrSocketFd);
        close(stdoutSocketFd);
        close(stdinSocketFd);
        return 1;
    }

    // This execution branch is for the child process
    if (childPid == 0) {

        // Close the socket server file descriptors because the child process
        // doesn't need to listen.
        close(stdinSocketFd);
        close(stdoutSocketFd);
        close(stderrSocketFd);

        // Redirect stdin, stdout, and stderr. Exit immediately if dup2 fails.
        if (dup2(clientStdinFd, STDIN_FILENO) == -1) {
            LOGE("dup2(stdin) failed: %s", strerror(errno));
            exit(1);
        }
        if (dup2(clientStdoutFd, STDOUT_FILENO) == -1) {
            LOGE("dup2(stdout) failed: %s", strerror(errno));
            exit(1);
        }
        if (dup2(clientStderrFd, STDERR_FILENO) == -1) {
            LOGE("dup2(stderr) failed: %s", strerror(errno));
            exit(1);
        }

        // Close the client socket file descriptors because now they're duplicated
        close(clientStdinFd);
        close(clientStdoutFd);
        close(clientStderrFd);

        // Execute the command
        LOGI("Starting child process command %s", command);

        // Use command directly, ensure last arg is NULL
        execl("/system/bin/sh", "sh", "-c", command, (char *)NULL);

        // Note that this code is executed only if execl fails.
        LOGE("execl() failed: %s", strerror(errno));
        exit(1);
    }

    // This execution branch is for the parent process
    if (childPid > 0) {

        // Wait for the child process to finish (ignore status like original)
        LOGI("Waiting for child process [%d] to exit", childPid);
        waitpid(childPid, NULL, 0);

        // Clean up all the server sockets
        shutdown(clientStdinFd, SHUT_RDWR);
        shutdown(clientStdoutFd, SHUT_RDWR);
        shutdown(clientStderrFd, SHUT_RDWR);

        // Parent closes its copy of the client sockets immediately after fork
        close(clientStdinFd);
        close(clientStdoutFd);
        close(clientStderrFd);

        // Clean up all the server sockets
        shutdown(stdinSocketFd, SHUT_RDWR);
        shutdown(stdoutSocketFd, SHUT_RDWR);
        shutdown(stderrSocketFd, SHUT_RDWR);

        close(stdinSocketFd);
        close(stdoutSocketFd);
        close(stderrSocketFd);

        LOGI("NativeShellProcess finished");
    }

    return 0;
}
