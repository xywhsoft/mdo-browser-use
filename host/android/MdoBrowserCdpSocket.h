#ifndef MDO_BROWSER_CDP_SOCKET_H
#define MDO_BROWSER_CDP_SOCKET_H

#include <stddef.h>

/* Private transport, not an Agent/extension protocol. Discovery is read-only,
 * same PID+UID only, with one absolute timeout and no TCP/path fallback. */
enum MdoCdpSocketCode {
    MDO_CDP_SOCKET_OK=0, MDO_CDP_SOCKET_ARGUMENTS, MDO_CDP_SOCKET_UNAVAILABLE,
    MDO_CDP_SOCKET_PEER, MDO_CDP_SOCKET_TIMEOUT, MDO_CDP_SOCKET_IO,
    MDO_CDP_SOCKET_HTTP, MDO_CDP_SOCKET_LIMIT, MDO_CDP_SOCKET_MEMORY,
    MDO_CDP_SOCKET_CLOSED, MDO_CDP_SOCKET_PROTOCOL, MDO_CDP_SOCKET_BUSY,
    MDO_CDP_SOCKET_RANDOM, MDO_CDP_SOCKET_EXPIRED
};
typedef struct MdoCdpDiscovery {
    unsigned char* Data;
    size_t Size, Capacity;
    enum MdoCdpSocketCode Code;
} MdoCdpDiscovery;

MdoCdpDiscovery mdoCdpDiscover(int TimeoutMs);
void mdoCdpDiscoveryFree(MdoCdpDiscovery* Reply);
const char* mdoCdpSocketCode(enum MdoCdpSocketCode Code);

#endif
