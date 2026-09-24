import { HttpError } from "../_shared/pupeye.ts";

export const SUPPORT_BAN_ACTIONS = [
  "issue_temporary",
  "issue_permanent",
  "extend_temporary",
  "convert_to_temporary",
  "convert_to_permanent",
  "attach_target",
  "revoke_ban",
  "clear_review",
  "record_appeal",
  "lookup",
] as const;

export type SupportBanAction = typeof SUPPORT_BAN_ACTIONS[number];

export type BanTargetInput = {
  type: "PLAYER" | "DISCORD" | "DEVICE_REPUTATION" | "INSTALLATION" | "WEB_INSTALLATION";
  playerUuid?: string;
  discordUserId?: string;
  deviceReputationUuid?: string;
  installationUuid?: string;
  webInstallationId?: string;
};

export type NormalizedIssueBanRequest = {
  kind: "temporary" | "permanent";
  reasonCode: string;
  publicReason: string;
  internalReason: string | null;
  expiresAt: string | null;
  actor: string;
  supportNote: string | null;
  targets: BanTargetInput[];
};

export function validateSupportAction(value: unknown): SupportBanAction {
  const action = typeof value === "string" ? value.trim() : "";
  if (!SUPPORT_BAN_ACTIONS.includes(action as SupportBanAction)) {
    throw new HttpError(400, "BAN_ACTION_INVALID", "Unsupported PupEye ban action.");
  }
  return action as SupportBanAction;
}

export function validateIssueBanRequest(
  body: Record<string, unknown>,
  nowEpochMs = Date.now(),
): NormalizedIssueBanRequest {
  const action = validateSupportAction(body.action);
  if (action !== "issue_temporary" && action !== "issue_permanent") {
    throw new HttpError(400, "BAN_ACTION_INVALID", "Issue-ban validation requires an issue action.");
  }

  const kind = action === "issue_temporary" ? "temporary" : "permanent";
  const reasonCode = requiredText(body.reasonCode, "reasonCode", 64).toUpperCase();
  const publicReason = requiredText(body.publicReason, "publicReason", 500);
  const internalReason = optionalText(body.internalReason, 2000);
  const actor = requiredText(body.actor, "actor", 80);
  const supportNote = optionalText(body.supportNote, 1000);
  const targets = validateTargets(body.targets);

  let expiresAt: string | null = null;
  if (kind === "temporary") {
    const raw = requiredText(body.expiresAt, "expiresAt", 64);
    const epoch = Date.parse(raw);
    if (!Number.isFinite(epoch) || epoch <= nowEpochMs) {
      throw new HttpError(400, "BAN_EXPIRY_INVALID", "Temporary ban requires a future expiry.");
    }
    expiresAt = new Date(epoch).toISOString();
  } else if (body.expiresAt !== undefined && body.expiresAt !== null && String(body.expiresAt).trim()) {
    throw new HttpError(400, "BAN_EXPIRY_INVALID", "Permanent ban cannot include expiresAt.");
  }

  return {
    kind,
    reasonCode,
    publicReason,
    internalReason,
    expiresAt,
    actor,
    supportNote,
    targets,
  };
}

export function validateTargets(value: unknown): BanTargetInput[] {
  if (!Array.isArray(value) || value.length < 1) {
    throw new HttpError(400, "BAN_TARGET_REQUIRED", "A global ban requires at least one target.");
  }
  if (value.length > 20) {
    throw new HttpError(400, "BAN_TARGET_LIMIT", "A global ban may contain at most 20 targets.");
  }

  return value.map((raw, index) => {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
      throw new HttpError(400, "BAN_TARGET_INVALID", `Ban target ${index + 1} is invalid.`);
    }
    const target = raw as Record<string, unknown>;
    const type = requiredText(target.type, "target.type", 32).toUpperCase() as BanTargetInput["type"];
    if (!["PLAYER", "DISCORD", "DEVICE_REPUTATION", "INSTALLATION", "WEB_INSTALLATION"].includes(type)) {
      throw new HttpError(400, "BAN_TARGET_INVALID", `Unsupported ban target type: ${type}.`);
    }

    const result: BanTargetInput = { type };
    if (type === "PLAYER") result.playerUuid = requiredUuid(target.playerUuid, "playerUuid");
    if (type === "DISCORD") result.discordUserId = requiredText(target.discordUserId, "discordUserId", 32);
    if (type === "DEVICE_REPUTATION") {
      result.deviceReputationUuid = requiredUuid(target.deviceReputationUuid, "deviceReputationUuid");
    }
    if (type === "INSTALLATION") {
      result.installationUuid = requiredUuid(target.installationUuid, "installationUuid");
    }
    if (type === "WEB_INSTALLATION") {
      result.webInstallationId = requiredText(target.webInstallationId, "webInstallationId", 128);
    }
    return result;
  });
}

export function requiredText(value: unknown, field: string, max: number): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new HttpError(400, "BAN_FIELD_REQUIRED", `Missing ${field}.`);
  }
  return value.trim().slice(0, max);
}

export function optionalText(value: unknown, max: number): string | null {
  return typeof value === "string" && value.trim() ? value.trim().slice(0, max) : null;
}

export function requiredUuid(value: unknown, field: string): string {
  const text = requiredText(value, field, 36).toLowerCase();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(text)) {
    throw new HttpError(400, "BAN_TARGET_INVALID", `Invalid ${field}.`);
  }
  return text;
}
