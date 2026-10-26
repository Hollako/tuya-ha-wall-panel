#ifndef PANEL_NETWORK_POLICY_H
#define PANEL_NETWORK_POLICY_H
enum { NETWORK_NONE, NETWORK_RECONNECT, NETWORK_CYCLE };
typedef struct { long bad_since, next_reconnect, next_cycle; } network_policy;
static void network_policy_init(network_policy *p, long now) {
    *p = (network_policy){.bad_since = -1, .next_reconnect = now + 30, .next_cycle = now + 120};
}
/* HTTP availability deliberately has no influence on Wi-Fi recovery. */
static int network_policy_step(network_policy *p, long now, int associated, int ipv4) {
    if (associated && ipv4) {
        p->bad_since = -1;
        p->next_reconnect = now + 30; p->next_cycle = now + 120;
        return NETWORK_NONE;
    }
    if (p->bad_since < 0) p->bad_since = now;
    if (now >= p->next_cycle && now - p->bad_since >= 120) {
        p->next_cycle = now + 300; p->next_reconnect = now + 30;
        return NETWORK_CYCLE;
    }
    if (now >= p->next_reconnect && now - p->bad_since >= (associated ? 60 : 30)) {
        p->next_reconnect = now + 30;
        return NETWORK_RECONNECT;
    }
    return NETWORK_NONE;
}
#endif
