#ifndef PANEL_FOREGROUND_POLICY_H
#define PANEL_FOREGROUND_POLICY_H
#include <string.h>
enum { FOREGROUND_UNKNOWN, FOREGROUND_BROWSER, FOREGROUND_TUYA_HOME, FOREGROUND_OTHER };
static int foreground_contains(const char *line, const char *end, const char *component) {
    const char *p = strstr(line, component);
    if (!p || p >= end) return 0;
    p += strlen(component);
    return p <= end && (*p == ' ' || *p == '}' || *p == '\r' || *p == '\n' || *p == 0);
}
static int foreground_parse(const char *dump) {
    const char *line = strstr(dump, "mResumedActivity:");
    if (!line) return FOREGROUND_UNKNOWN;
    const char *end = strchr(line, '\n');
    if (!end) end = line + strlen(line);
    if (foreground_contains(line, end, "acr.browser.barebones/acr.browser.lightning.activity.MainActivity")) return FOREGROUND_BROWSER;
    if (foreground_contains(line, end, "com.sznaner.bgmz9/.MainActivity") || foreground_contains(line, end, "com.sznaner.myapp/.MainActivity")) return FOREGROUND_TUYA_HOME;
    return FOREGROUND_OTHER;
}
static int foreground_restore(int launched, int foreground, long now, long next_attempt) {
    return launched && foreground == FOREGROUND_TUYA_HOME && now >= next_attempt;
}
#endif
