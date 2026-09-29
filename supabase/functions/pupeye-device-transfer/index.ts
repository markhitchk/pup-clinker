import {
  assertPublishableRequest,
  audit,
  createAdminClient,
  errorResponse,
  issueSession,
  json,
  verifyEnvelope,
} from "../_shared/pupeye.ts";
import { HttpError } from "../_shared/http.ts";
import { assertGlobalEnforcementAllowed } from "../_shared/global-enforcement.ts";
import {
  parseDeviceTransferClaim,
  requiresManualDeviceTransferApproval,
  sourcePublicKeyMatchesFingerprint,
  verifySignedDeviceTransferClaim,
  type DeviceTransferClaim,
} from "./policy.ts";

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json(), "device-transfer");
    const claim = parseDeviceTransferClaim(envelope.payload);
    const admin = createAdminClient();

    const { data: byPlayerId, error: playerIdError } = await admin
      .from("pupeye_players")
      .select("*")
      .eq("player_id", claim.playerId)
      .maybeSingle();
    if (playerIdError) throw playerIdError;

    const { data: byFriendCode, error: friendCodeError } = await admin
      .from("pupeye_players")
      .select("*")
      .eq("friend_code", claim.friendCode)
      .maybeSingle();
    if (friendCodeError) throw friendCodeError;

    if (!byPlayerId || !byFriendCode || byPlayerId.id !== byFriendCode.id) {
      throw new HttpError(
        404,
        "MIGRATION_PLAYER_NOT_FOUND",
        "The Player ID and Friend Code in this authenticated save are not registered to the same Puppy Clicker player.",
      );
    }
    const player = byPlayerId;

    const sourceInstallation = await verifySourceOwnership(admin, player, claim);
    const signedMigrationClaim = !requiresManualDeviceTransferApproval(claim);

    const { data: activeInstallation, error: activeError } = await admin
      .from("pupeye_installations")
      .select("*")
      .eq("player_uuid", player.id)
      .eq("active", true)
      .is("revoked_at", null)
      .maybeSingle();
    if (activeError) throw activeError;

    await assertGlobalEnforcementAllowed(admin, {
      player,
      installation: activeInstallation ?? sourceInstallation,
      discordUserId: player.discord_user_id ?? null,
      requestAction: "device-transfer",
    });

    const { data: exactInstallation, error: exactError } = await admin
      .from("pupeye_installations")
      .select("*")
      .eq("installation_id", envelope.installationId)
      .maybeSingle();
    if (exactError) throw exactError;

    if (
      exactInstallation &&
      (
        exactInstallation.device_key_id !== envelope.deviceKeyId ||
        exactInstallation.public_key_b64 !== envelope.publicKey
      )
    ) {
      throw new HttpError(
        409,
        "INSTALLATION_IDENTITY_CONFLICT",
        "This new-device installation ID is already registered with a different signing key.",
      );
    }

    if (
      exactInstallation &&
      exactInstallation.player_uuid === player.id &&
      exactInstallation.active &&
      !exactInstallation.revoked_at
    ) {
      const session = await issueSession(admin, player.id, exactInstallation.id);
      return json(200, {
        ok: true,
        migrated: true,
        alreadyMigrated: true,
        sessionToken: session.token,
        expiresAtEpochMs: session.expiresAtEpochMs,
        backendPlayerId: player.id,
        backendInstallationId: exactInstallation.id,
        message: "This device is already authorized for the imported Puppy Clicker player.",
      });
    }

    let provisionalPlayer: any = null;
    if (exactInstallation && exactInstallation.player_uuid !== player.id) {
      const { data, error } = await admin
        .from("pupeye_players")
        .select("*")
        .eq("id", exactInstallation.player_uuid)
        .maybeSingle();
      if (error) throw error;
      provisionalPlayer = data;
      if (!provisionalPlayer) {
        throw new HttpError(
          409,
          "INSTALLATION_IDENTITY_CONFLICT",
          "The provisional new-device player record could not be verified.",
        );
      }

      await assertGlobalEnforcementAllowed(admin, {
        player: provisionalPlayer,
        installation: exactInstallation,
        discordUserId: provisionalPlayer.discord_user_id ?? null,
        requestAction: "device-transfer",
      });

      if (!(await isSafeProvisionalInstallation(admin, exactInstallation, provisionalPlayer))) {
        throw new HttpError(
          409,
          "DEVICE_TRANSFER_REINSTALL_REQUIRED",
          "This new installation already has protected activity and cannot be reassigned automatically. Clear Puppy Clicker app data on the new phone, reopen the updated app, then import the backup before starting a new game.",
          { supportCode: claim.supportCode },
        );
      }
    }

    let { data: migration, error: migrationError } = await admin
      .from("pupeye_device_migrations")
      .select("*")
      .eq("player_uuid", player.id)
      .eq("requested_installation_id", envelope.installationId)
      .eq("requested_device_key_id", envelope.deviceKeyId)
      .maybeSingle();
    if (migrationError) throw migrationError;

    if (!migration || migration.status === "pending") {
      let pending = migration;
      const autoApprove = signedMigrationClaim;
      const reviewedAt = autoApprove ? new Date().toISOString() : null;

      if (!pending) {
        const { data, error } = await admin
          .from("pupeye_device_migrations")
          .insert({
            player_uuid: player.id,
            from_installation_uuid: activeInstallation?.id ?? sourceInstallation.id,
            requested_installation_id: envelope.installationId,
            requested_device_key_id: envelope.deviceKeyId,
            requested_public_key_b64: envelope.publicKey,
            requested_support_code: claim.supportCode,
            requested_device_model: claim.deviceModel,
            requested_app_version: claim.appVersion,
            status: autoApprove ? "approved" : "pending",
            requested_at: new Date().toISOString(),
            reviewed_at: reviewedAt,
            support_note: autoApprove
              ? "Automatically approved from a verified signed migration claim."
              : null,
          })
          .select("*")
          .single();
        if (error) throw error;
        pending = data;
      } else {
        const update: Record<string, unknown> = {
          requested_public_key_b64: envelope.publicKey,
          requested_support_code: claim.supportCode,
          requested_device_model: claim.deviceModel,
          requested_app_version: claim.appVersion,
          requested_at: new Date().toISOString(),
        };
        if (autoApprove) {
          update.status = "approved";
          update.reviewed_at = reviewedAt;
          update.support_note =
            "Automatically approved from a verified signed migration claim.";
        }

        const { data, error } = await admin
          .from("pupeye_device_migrations")
          .update(update)
          .eq("id", pending.id)
          .select("*")
          .single();
        if (error) throw error;
        pending = data;
      }

      migration = pending;

      if (!autoApprove) {
        await audit(
          admin,
          "DEVICE_MIGRATION_REQUIRED",
          "review",
          {
            migrationId: pending.id,
            supportCode: claim.supportCode,
            saveId: claim.saveId,
            sourceInstallationId: claim.sourceInstallationId,
          },
          player.id,
          activeInstallation?.id ?? sourceInstallation.id,
        );

        return json(409, {
          code: "DEVICE_MIGRATION_REQUIRED",
          message:
            "This older backup is authentic, but it needs one-time Support approval. For immediate transfer, export a fresh backup from the updated old phone and import it here.",
          migrationId: pending.id,
          supportCode: claim.supportCode,
        });
      }

      await audit(
        admin,
        "DEVICE_MIGRATION_AUTO_APPROVED",
        "info",
        {
          migrationId: pending.id,
          supportCode: claim.supportCode,
          saveId: claim.saveId,
          sourceInstallationId: claim.sourceInstallationId,
        },
        player.id,
        activeInstallation?.id ?? sourceInstallation.id,
      );
    }

    if (migration.status !== "approved") {
      throw new HttpError(
        409,
        "DEVICE_MIGRATION_NOT_APPROVED",
        "This device-transfer request is not approved.",
        { supportCode: claim.supportCode, migrationId: migration.id, status: migration.status },
      );
    }

    const now = new Date().toISOString();

    if (activeInstallation && activeInstallation.id !== exactInstallation?.id) {
      await admin.from("pupeye_sessions")
        .update({ revoked_at: now })
        .eq("installation_uuid", activeInstallation.id)
        .is("revoked_at", null);
      const { error } = await admin.from("pupeye_installations")
        .update({ active: false, revoked_at: now })
        .eq("id", activeInstallation.id);
      if (error) throw error;
    }

    let migratedInstallation: any;
    if (exactInstallation) {
      await admin.from("pupeye_sessions")
        .update({ revoked_at: now })
        .eq("installation_uuid", exactInstallation.id)
        .is("revoked_at", null);

      const { data, error } = await admin.from("pupeye_installations")
        .update({
          player_uuid: player.id,
          support_code: claim.supportCode,
          device_model: claim.deviceModel,
          platform: claim.platform,
          app_version: claim.appVersion,
          active: true,
          revoked_at: null,
          integrity_state: "clean",
          max_save_generation: Math.max(
            Number(exactInstallation.max_save_generation ?? 0),
            claim.generation,
          ),
          last_seen_at: now,
        })
        .eq("id", exactInstallation.id)
        .select("*")
        .single();
      if (error) throw error;
      migratedInstallation = data;

      const { error: linkDeleteError } = await admin
        .from("pupeye_identity_links")
        .delete()
        .eq("link_type", "PLAYER_INSTALLATION")
        .eq("installation_uuid", exactInstallation.id);
      if (linkDeleteError) throw linkDeleteError;

      const { error: linkInsertError } = await admin
        .from("pupeye_identity_links")
        .insert({
          link_type: "PLAYER_INSTALLATION",
          player_uuid: player.id,
          installation_uuid: exactInstallation.id,
          confidence: "authoritative",
          evidence_code: "APPROVED_SAVE_MIGRATION",
        });
      if (linkInsertError && linkInsertError.code !== "23505") throw linkInsertError;
    } else {
      const reputation = await createDeviceReputation(
        admin,
        claim.platform,
        claim.appVersion,
      );
      const { data, error } = await admin.from("pupeye_installations")
        .insert({
          player_uuid: player.id,
          installation_id: envelope.installationId,
          device_key_id: envelope.deviceKeyId,
          public_key_b64: envelope.publicKey,
          support_code: claim.supportCode,
          device_model: claim.deviceModel,
          platform: claim.platform,
          app_version: claim.appVersion,
          active: true,
          max_save_generation: claim.generation,
          device_reputation_uuid: reputation.id,
        })
        .select("*")
        .single();
      if (error) throw error;
      migratedInstallation = data;
      await finishDeviceIdentity(
        admin,
        player.id,
        migratedInstallation.id,
        reputation.id,
      );
    }

    const { error: headError } = await admin.from("pupeye_save_heads")
      .upsert({
        installation_uuid: migratedInstallation.id,
        generation: claim.generation,
        support_code: claim.supportCode,
        last_reason: "device-transfer",
        hard_flags: [],
        last_save_id: claim.saveId,
        last_payload_hash_sha256: claim.payloadHashSha256,
        updated_at: now,
      }, { onConflict: "installation_uuid" });
    if (headError) throw headError;

    const { error: migrationUpdateError } = await admin
      .from("pupeye_device_migrations")
      .update({ status: "consumed", consumed_at: now })
      .eq("id", migration.id);
    if (migrationUpdateError) throw migrationUpdateError;

    await audit(
      admin,
      "DEVICE_MIGRATION_CONSUMED",
      "info",
      {
        migrationId: migration.id,
        supportCode: claim.supportCode,
        saveId: claim.saveId,
        provisionalInstallationReused: Boolean(exactInstallation),
      },
      player.id,
      migratedInstallation.id,
    );

    const session = await issueSession(admin, player.id, migratedInstallation.id);
    return json(200, {
      ok: true,
      migrated: true,
      sessionToken: session.token,
      expiresAtEpochMs: session.expiresAtEpochMs,
      backendPlayerId: player.id,
      backendInstallationId: migratedInstallation.id,
      message: "Device transfer approved. Puppy Clicker can now restore this backup.",
    });
  } catch (error) {
    return errorResponse(error);
  }
});

async function verifySourceOwnership(
  admin: any,
  player: any,
  claim: DeviceTransferClaim,
): Promise<any> {
  if (!sourcePublicKeyMatchesFingerprint(claim)) {
    throw new HttpError(
      400,
      "MIGRATION_SOURCE_KEY_INVALID",
      "The backup source key fingerprint is invalid.",
    );
  }

  const { data: sourceInstallation, error: sourceError } = await admin
    .from("pupeye_installations")
    .select("*")
    .eq("installation_id", claim.sourceInstallationId)
    .maybeSingle();
  if (sourceError) throw sourceError;
  if (
    !sourceInstallation ||
    sourceInstallation.player_uuid !== player.id ||
    sourceInstallation.device_key_id !== claim.sourceDeviceKeyId ||
    sourceInstallation.public_key_b64 !== claim.sourcePublicKey
  ) {
    throw new HttpError(
      403,
      "MIGRATION_SOURCE_NOT_REGISTERED",
      "The backup was not signed by a registered installation for this Puppy Clicker player.",
    );
  }

  if (verifySignedDeviceTransferClaim(claim)) {
    return sourceInstallation;
  }

  const { data: attestation, error: attestationError } = await admin
    .from("pupeye_save_attestations")
    .select("*")
    .eq("player_uuid", player.id)
    .eq("installation_uuid", sourceInstallation.id)
    .eq("save_id", claim.saveId)
    .eq("generation", claim.generation)
    .eq("payload_hash_sha256", claim.payloadHashSha256)
    .eq("device_key_id", claim.sourceDeviceKeyId)
    .maybeSingle();
  if (attestationError) throw attestationError;
  if (!attestation) {
    throw new HttpError(
      409,
      "MIGRATION_SAVE_NOT_ATTESTED",
      "This older backup is authentic locally but has no server attestation. Open the old phone online and export a fresh backup, then try again.",
    );
  }

  return sourceInstallation;
}

async function isSafeProvisionalInstallation(
  admin: any,
  installation: any,
  player: any,
): Promise<boolean> {
  if (
    !installation.active ||
    installation.revoked_at ||
    installation.integrity_state !== "clean" ||
    player.discord_user_id
  ) return false;

  const [{ count: transactionCount, error: transactionError }, {
    count: attestationCount,
    error: attestationError,
  }, { count: installationCount, error: installationCountError }] =
    await Promise.all([
      admin.from("pupeye_transactions")
        .select("transaction_id", { count: "exact", head: true })
        .eq("installation_uuid", installation.id),
      admin.from("pupeye_save_attestations")
        .select("id", { count: "exact", head: true })
        .eq("installation_uuid", installation.id),
      admin.from("pupeye_installations")
        .select("id", { count: "exact", head: true })
        .eq("player_uuid", player.id),
    ]);

  if (transactionError) throw transactionError;
  if (attestationError) throw attestationError;
  if (installationCountError) throw installationCountError;

  return (transactionCount ?? 0) === 0 &&
    (attestationCount ?? 0) === 0 &&
    (installationCount ?? 0) === 1;
}

async function createDeviceReputation(
  admin: any,
  platform: string,
  appVersion: string | null,
) {
  const { data, error } = await admin.from("pupeye_device_reputation")
    .insert({
      platform,
      reputation_state: "clean",
      metadata: appVersion ? { appVersion } : {},
    })
    .select("*")
    .single();
  if (error) throw error;
  return data;
}

async function finishDeviceIdentity(
  admin: any,
  playerUuid: string,
  installationUuid: string,
  deviceReputationUuid: string,
): Promise<void> {
  const { error: reputationError } = await admin.from("pupeye_device_reputation")
    .update({
      created_from_installation_uuid: installationUuid,
      last_seen_at: new Date().toISOString(),
    })
    .eq("id", deviceReputationUuid);
  if (reputationError) throw reputationError;

  for (const row of [
    {
      link_type: "PLAYER_INSTALLATION",
      player_uuid: playerUuid,
      installation_uuid: installationUuid,
      confidence: "authoritative",
      evidence_code: "APPROVED_SAVE_MIGRATION",
    },
    {
      link_type: "INSTALLATION_DEVICE",
      installation_uuid: installationUuid,
      device_reputation_uuid: deviceReputationUuid,
      confidence: "authoritative",
      evidence_code: "APPROVED_SAVE_MIGRATION",
    },
  ]) {
    const { error } = await admin.from("pupeye_identity_links").insert(row);
    if (error && error.code !== "23505") throw error;
  }
}
