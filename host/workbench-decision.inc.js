  // A decision can arrive while the workbench covers the entire conversation.
  // Suspend presentation only: native resources and the saved tab layout stay.
  let decisionInspection = false;
  const decisionPending = () => Boolean(shell.querySelector(".composer-region")?.hasAttribute("data-decision-pending"));
  const workbenchCoversConversation = () => layout.maximized || innerWidth <= 1100;
  const workbenchVisible = () => !stopped && layout.open &&
    !(decisionPending() && workbenchCoversConversation() && !decisionInspection);
  const revealConversation = () => {
    decisionInspection = false;
    if (stopped || !layout.open || !decisionPending() || !workbenchCoversConversation()) return false;
    render();
    return true;
  };
