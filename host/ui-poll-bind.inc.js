  };
  const pump = createUiPollPump({ poll, delay: () => document.hidden ? 10000 : 1500 });
  const stopHints = liveConnection.subscribe(event => {
    if (event.type === "ui_pending" || (event.type === "status" && event.connected)) pump.wake();
  });
  document.addEventListener("visibilitychange", pump.wake);
  window.addEventListener("focus", pump.wake);
  pump.wake();
