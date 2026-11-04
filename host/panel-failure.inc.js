  const fail = (root, error, retry, closeMissing) => {
    if (closeMissing && error.status === 404 && ["resource_missing", "browser_missing"].includes(error.code)) {
      root.replaceChildren(make("h2", "", "页面已结束"),
        make("p", "wb-error", "原页面已经关闭或被移除，无法恢复。关闭此标签页后，可从“＋”打开新页面；不会自动重新打开原网页。"),
        button("关闭标签页", closeMissing));
      return;
    }
    root.replaceChildren(make("p", "wb-error", error.message || String(error)), button("重新读取", retry));
  };
