#include <jni.h>
#include <android/log.h>
#include <cerrno>
#include <cstring>
#include <algorithm>
#include <fcntl.h>
#include <signal.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define LOG_TAG "ToolboxPty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    return flags < 0 ? -1 : fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_yanyuan_toolbox_terminal_NativePty_nativeOpen(
        JNIEnv* env, jclass, jstring program, jobjectArray args, jstring cwd, jint rows, jint cols,
        jobjectArray environmentKeys, jobjectArray environmentValues) {
    const char* programChars = env->GetStringUTFChars(program, nullptr);
    const char* cwdChars = env->GetStringUTFChars(cwd, nullptr);
    if (!programChars || !cwdChars) return 0;

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0 || grantpt(master) != 0 || unlockpt(master) != 0) {
        if (master >= 0) close(master);
        env->ReleaseStringUTFChars(program, programChars);
        env->ReleaseStringUTFChars(cwd, cwdChars);
        return 0;
    }
    const char* slaveName = ptsname(master);
    if (!slaveName) {
        close(master);
        env->ReleaseStringUTFChars(program, programChars);
        env->ReleaseStringUTFChars(cwd, cwdChars);
        return 0;
    }
    const int count = env->GetArrayLength(args);
    const int environmentCount = environmentKeys && environmentValues
            ? std::min(env->GetArrayLength(environmentKeys), env->GetArrayLength(environmentValues))
            : 0;
    std::vector<std::string> argValues;
    argValues.reserve(count);
    for (int i = 0; i < count; ++i) {
        jstring item = static_cast<jstring>(env->GetObjectArrayElement(args, i));
        const char* value = env->GetStringUTFChars(item, nullptr);
        argValues.emplace_back(value ? value : "");
        if (value) env->ReleaseStringUTFChars(item, value);
        env->DeleteLocalRef(item);
    }
    std::vector<char*> argv;
    argv.reserve(count + 2);
    argv.push_back(const_cast<char*>(programChars));
    for (auto& value : argValues) argv.push_back(value.data());
    argv.push_back(nullptr);
    std::vector<std::string> environmentEntries;
    environmentEntries.reserve(environmentCount);
    for (int i = 0; i < environmentCount; ++i) {
        jstring key = static_cast<jstring>(env->GetObjectArrayElement(environmentKeys, i));
        jstring value = static_cast<jstring>(env->GetObjectArrayElement(environmentValues, i));
        const char* keyChars = key ? env->GetStringUTFChars(key, nullptr) : nullptr;
        const char* valueChars = value ? env->GetStringUTFChars(value, nullptr) : nullptr;
        if (keyChars && valueChars && std::strchr(keyChars, '=') == nullptr) {
            environmentEntries.emplace_back(std::string(keyChars) + "=" + valueChars);
        }
        if (keyChars) env->ReleaseStringUTFChars(key, keyChars);
        if (valueChars) env->ReleaseStringUTFChars(value, valueChars);
        if (key) env->DeleteLocalRef(key);
        if (value) env->DeleteLocalRef(value);
    }
    const std::string programCopy(programChars);
    const std::string cwdCopy(cwdChars);
    const std::string slaveCopy(slaveName);
    pid_t child = fork();
    if (child < 0) {
        close(master);
        env->ReleaseStringUTFChars(program, programChars);
        env->ReleaseStringUTFChars(cwd, cwdChars);
        return 0;
    }
    if (child == 0) {
        close(master);
        setsid();
        int slave = open(slaveName, O_RDWR);
        if (slave < 0) _exit(127);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        if (chdir(cwdCopy.c_str()) != 0) _exit(126);
        if (!environmentEntries.empty()) {
            clearenv();
            for (const auto& entry : environmentEntries) putenv(const_cast<char*>(entry.c_str()));
        }
        execv(programCopy.c_str(), argv.data());
        _exit(127);
    }

    set_nonblocking(master);
    struct winsize size{};
    size.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    size.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);
    ioctl(master, TIOCSWINSZ, &size);
    env->ReleaseStringUTFChars(program, programChars);
    env->ReleaseStringUTFChars(cwd, cwdChars);
    return (static_cast<jlong>(child) << 32) | static_cast<unsigned int>(master);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_yanyuan_toolbox_terminal_NativePty_nativeRead(JNIEnv* env, jclass, jlong handle, jint maxBytes) {
    int fd = static_cast<int>(handle & 0xffffffff);
    if (fd < 0) return nullptr;
    const int limit = maxBytes > 0 ? maxBytes : 8192;
    std::string buffer;
    buffer.resize(limit);
    ssize_t n = read(fd, buffer.data(), limit);
    if (n <= 0) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(n));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(n), reinterpret_cast<const jbyte*>(buffer.data()));
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yanyuan_toolbox_terminal_NativePty_nativeWrite(JNIEnv* env, jclass, jlong handle, jbyteArray data) {
    int fd = static_cast<int>(handle & 0xffffffff);
    if (fd < 0 || !data) return -1;
    jsize length = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    ssize_t result = write(fd, bytes, static_cast<size_t>(length));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return static_cast<jint>(result);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yanyuan_toolbox_terminal_NativePty_nativeResize(JNIEnv*, jclass, jlong handle, jint rows, jint cols) {
    int fd = static_cast<int>(handle & 0xffffffff);
    struct winsize size{};
    size.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    size.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);
    return ioctl(fd, TIOCSWINSZ, &size);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yanyuan_toolbox_terminal_NativePty_nativeSignal(JNIEnv*, jclass, jlong handle, jint signalNumber) {
    pid_t child = static_cast<pid_t>(handle >> 32);
    return kill(-child, signalNumber);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yanyuan_toolbox_terminal_NativePty_nativeClose(JNIEnv*, jclass, jlong handle) {
    int fd = static_cast<int>(handle & 0xffffffff);
    pid_t child = static_cast<pid_t>(handle >> 32);
    kill(-child, SIGHUP);
    kill(-child, SIGTERM);
    int status = 0;
    waitpid(child, &status, WNOHANG);
    kill(-child, SIGKILL);
    waitpid(child, &status, 0);
    if (fd >= 0) close(fd);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}
