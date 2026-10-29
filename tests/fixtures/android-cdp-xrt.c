/* Link the exact pinned XRT primitives, not a substitute frame/crypto model. */
#define XRT_MODULE_WEBSOCKET_HANDSHAKE
#define XRT_MODULE_WEBSOCKET_MESSAGE
#define XRT_MODULE_RANDOM_SECURE
#define XRT_MODULE_JSON_READ
#define XRT_IMPLEMENTATION
#include "xrt.h"
