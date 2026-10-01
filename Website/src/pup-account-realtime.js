/**
 * Framework-neutral Supabase Realtime listener for Puppy Clicker Pup Accounts.
 *
 * The realtime topic is returned only by the authenticated Pup Account API.
 * Broadcast payloads are invalidation metadata only; never treat them as authoritative save data.
 */
export function createPupAccountRealtime({
  supabaseUrl,
  publishableKey,
  realtimeTopic,
  onChanged,
  onStatus = () => {},
}) {
  if (!/^https:\/\//.test(supabaseUrl)) throw new Error("A HTTPS Supabase URL is required");
  if (!publishableKey) throw new Error("A Supabase publishable key is required");
  if (!/^pup-account:[0-9a-f-]{36}$/i.test(realtimeTopic)) {
    throw new Error("Invalid Pup Account realtime topic");
  }

  let socket = null;
  let heartbeat = null;
  let reconnect = null;
  let stopped = false;
  let attempt = 0;
  let ref = 1;

  const websocketUrl =
    supabaseUrl.replace(/^https:/, "wss:").replace(/\/$/, "") +
    "/realtime/v1/websocket?apikey=" +
    encodeURIComponent(publishableKey) +
    "&vsn=1.0.0";

  function send(event, topic, payload, joinRef = null) {
    if (!socket || socket.readyState !== WebSocket.OPEN) return false;
    ref += 1;
    socket.send(JSON.stringify({
      topic,
      event,
      payload,
      ref: String(ref),
      join_ref: joinRef,
    }));
    return true;
  }

  function connect() {
    if (stopped || socket) return;
    onStatus("connecting");
    socket = new WebSocket(websocketUrl);

    socket.addEventListener("open", () => {
      if (stopped) return;
      attempt = 0;
      onStatus("connected");
      socket.send(JSON.stringify({
        topic: "realtime:" + realtimeTopic,
        event: "phx_join",
        payload: {
          config: {
            broadcast: { ack: false, self: false },
            presence: { enabled: false },
            postgres_changes: [],
            private: false,
          },
        },
        ref: "1",
        join_ref: "1",
      }));

      clearInterval(heartbeat);
      heartbeat = setInterval(() => {
        send("heartbeat", "phoenix", {}, null);
      }, 20_000);
    });

    socket.addEventListener("message", (event) => {
      let message;
      try {
        message = JSON.parse(event.data);
      } catch {
        return;
      }
      if (message?.event !== "broadcast") return;
      if (message?.topic !== "realtime:" + realtimeTopic) return;
      const broadcast = message?.payload;
      if (broadcast?.event !== "changed") return;
      const payload = broadcast?.payload ?? {};
      if (!["save", "account", "identity", "device"].includes(payload.kind)) return;
      onChanged(payload);
    });

    socket.addEventListener("close", () => {
      socket = null;
      clearInterval(heartbeat);
      heartbeat = null;
      onStatus(stopped ? "stopped" : "disconnected");
      scheduleReconnect();
    });

    socket.addEventListener("error", () => {
      // close will drive the reconnect path.
    });
  }

  function scheduleReconnect() {
    if (stopped || reconnect) return;
    const delay = Math.min(30_000, 1_000 * (2 ** Math.min(attempt++, 5)));
    reconnect = setTimeout(() => {
      reconnect = null;
      connect();
    }, delay);
  }

  connect();

  return {
    stop() {
      stopped = true;
      clearTimeout(reconnect);
      reconnect = null;
      clearInterval(heartbeat);
      heartbeat = null;
      if (socket) socket.close(1000, "Website realtime stopped");
      socket = null;
      onStatus("stopped");
    },
  };
}
