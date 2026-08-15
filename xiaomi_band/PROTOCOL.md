# Band command protocol v1

The wearable sends UTF-8 JSON through `system.interconnect`:

```json
{
  "version": 1,
  "type": "kindle.page",
  "action": "next",
  "requestId": "1754064000000-1"
}
```

- `action` is `previous` or `next`.
- `requestId` is unique per press and contains 1–80 ASCII letters, digits, `.`, `_`, `:`, or `-`.
- Android rejects payloads larger than 2 KiB, malformed UTF-8, unknown versions/types/actions, and recently repeated request IDs.

Android immediately acknowledges whether the command was accepted into its local queue:

```json
{
  "version": 1,
  "type": "kindle.page.ack",
  "requestId": "1754064000000-1",
  "status": "accepted"
}
```

Possible statuses are `accepted`, `duplicate`, `remote_off`, and `invalid`. `accepted` means the phone accepted the command; it is not a guarantee that the later SSH operation succeeded.

The acknowledgement is diagnostic only. The wearable UI does not wait for it: animation, the 18-bead counter, and optional vibration are driven by the local swipe so a slow or missing acknowledgement cannot block the next page gesture.
