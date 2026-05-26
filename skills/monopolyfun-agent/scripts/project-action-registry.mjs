export const PROJECT_ACTIONS = Object.freeze({
  "create-project": action("create-project", "Create Project", "Project Publishing", {
    write: true,
    reply: "Project created and initial task generated.",
  }),
  "invite-role": action("invite-role", "Invite Role", "Project Roles", {
    write: true,
    workbench: true,
    reply: "Project role invitation sent.",
  }),
  "accept-invite": action("accept-invite", "Accept Invite", "Project Roles", {
    write: true,
    workbench: true,
    reply: "Project invitation accepted.",
  }),
  "claim-task": action("claim-task", "Claim Task", "Project Delivery", {
    write: true,
    workbench: true,
    reply: "Current task claimed.",
  }),
  "develop-task": action("develop-task", "Develop Task", "Project Delivery", {
    write: true,
    workbench: true,
    reply: "Development task advanced.",
  }),
  "submit-proof": action("submit-proof", "Submit Proof", "Project Delivery", {
    write: true,
    workbench: true,
    reply: "Proof submitted.",
  }),
  "review-proof": action("review-proof", "Review Proof", "Project Review", {
    write: true,
    workbench: true,
    reply: "Review action recorded.",
  }),
  "create-workthread": action("create-workthread", "Create WorkThread", "Project Bounty", {
    write: true,
    reply: "WorkThread created.",
  }),
  "claim-workthread": action("claim-workthread", "Claim WorkThread", "Project Bounty", {
    write: true,
    workbench: true,
    reply: "WorkThread claimed.",
  }),
  "submit-workthread-result": action("submit-workthread-result", "Submit WorkThread Result", "Project Bounty", {
    write: true,
    workbench: true,
    reply: "WorkThread result submitted.",
  }),
  "review-workthread": action("review-workthread", "Review WorkThread", "Project Bounty", {
    write: true,
    workbench: true,
    reply: "WorkThread review recorded.",
  }),
  "upsert-revenue-address": action("upsert-revenue-address", "Bind Revenue Address", "Project Rewards", {
    write: true,
    reply: "Revenue address bound.",
  }),
  "create-distribution": action("create-distribution", "Create Distribution", "Project Rewards", {
    write: true,
    reply: "Revenue distribution created.",
  }),
  "claim-revenue": action("claim-revenue", "Claim Revenue", "Project Rewards", {
    write: true,
    workbench: true,
    reply: "Revenue claim recorded.",
  }),
  "create-validation-launch": action("create-validation-launch", "Create Validation Launch", "Project Validation", {
    write: true,
    reply: "Validation launch created.",
  }),
  "publish-validation-launch": action("publish-validation-launch", "Publish Validation Launch", "Project Validation", {
    write: true,
    reply: "Validation launch published.",
  }),
  "create-validation-task": action("create-validation-task", "Create Validation Task", "Project Validation", {
    write: true,
    reply: "Validation task created.",
  }),
  "claim-validation-task": action("claim-validation-task", "Claim Validation Task", "Project Validation", {
    write: true,
    reply: "Validation task claimed.",
  }),
  "submit-validation-proof": action("submit-validation-proof", "Submit Validation Proof", "Project Validation", {
    write: true,
    reply: "Validation proof submitted.",
  }),
  "review-validation-proof": action("review-validation-proof", "Review Validation Proof", "Project Validation", {
    write: true,
    workbench: true,
    reply: "Validation review recorded.",
  }),
  "create-feedback": action("create-feedback", "Create Feedback", "Project Feedback", {
    write: true,
    workbench: true,
    reply: "Feedback created.",
  }),
  "resolve-feedback": action("resolve-feedback", "Resolve Feedback", "Project Feedback", {
    write: true,
    workbench: true,
    reply: "Feedback resolved.",
  }),
  "settle-validation-launch": action("settle-validation-launch", "Settle Validation Rewards", "Project Rewards", {
    write: true,
    workbench: true,
    reply: "Validation rewards settled.",
  }),
  "approve-share-release": action("approve-share-release", "Approve Share Release", "Project Rewards", {
    write: true,
    workbench: true,
    reply: "Share release approval submitted.",
  }),
  "bind-channel": action("bind-channel", "Bind Channel", "Project Maintenance", {
    write: true,
    reply: "Collaboration source binding processed.",
  }),
  "archive-discussion": action("archive-discussion", "Archive Discussion", "Project Maintenance", {
    write: true,
    reply: "External discussion archived.",
  }),
  "create-appeal": action("create-appeal", "Create Appeal", "Order Appeal", {
    write: true,
    workbench: true,
    reply: "Appeal created.",
  }),
  "resolve-appeal": action("resolve-appeal", "Resolve Appeal", "Order Appeal", {
    write: true,
    workbench: true,
    reply: "Appeal resolved.",
  }),
});

export function actionEntry(actionKey) {
  return PROJECT_ACTIONS[actionKey] ?? null;
}

export function actionKeysByFlag(flag) {
  return new Set(Object.values(PROJECT_ACTIONS).filter((entry) => entry[flag]).map((entry) => entry.key));
}

export function actionDocPath(actionKey) {
  return actionEntry(actionKey)?.doc ?? null;
}

export function actionReply(actionKey, projectNo) {
  const entry = actionEntry(actionKey);
  if (!entry) {
    return "";
  }
  return projectNo ? `Project ${projectNo}: ${entry.reply}` : entry.reply;
}

function action(key, label, group, options) {
  return Object.freeze({
    key,
    label,
    group,
    doc: `actions/${key}.md`,
    write: Boolean(options.write),
    workbench: Boolean(options.workbench),
    reply: options.reply,
  });
}
