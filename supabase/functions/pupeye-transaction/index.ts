import {
  HttpError,
  assertPublishableRequest,
  createAdminClient,
  audit,
  authenticateSession,
  errorResponse,
  json,
  requiredNumber,
  requiredString,
  verifyEnvelope,
} from "../_shared/pupeye.ts";

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
      const envelope = verifyEnvelope(await req.json(), "economy-transaction");
      const admin = createAdminClient();
      const session = await authenticateSession(req, admin, envelope);

      const transactionId = requiredString(envelope.payload.transactionId, "transactionId").slice(0, 160);
      const source = requiredString(envelope.payload.source, "source").slice(0, 40);
      const details = requiredString(envelope.payload.details, "details").slice(0, 360);
      const generation = requiredNumber(envelope.payload.generation, "generation");

      if (!Number.isSafeInteger(generation) || generation < 1) {
        throw new HttpError(400, "SAVE_GENERATION_INVALID", "Invalid economy generation.");
      }
      if (generation < Number(session.installation.max_save_generation ?? 0)) {
        await audit(
          admin,
          "ECONOMY_ROLLBACK",
          "review",
          { transactionId, generation, maxGeneration: session.installation.max_save_generation },
          session.player.id,
          session.installation.id,
        );
        await admin.from("pupeye_installations")
          .update({ integrity_state: "review" })
          .eq("id", session.installation.id);
        throw new HttpError(409, "SAVE_ROLLBACK", "Pupeye rejected an economy transaction from an older save generation.");
      }

      const { data: existing, error: existingError } = await admin
        .from("pupeye_transactions")
        .select("transaction_id")
        .eq("transaction_id", transactionId)
        .maybeSingle();
      if (existingError) throw existingError;
      if (existing) {
        await audit(
          admin,
          "DUPLICATE_TRANSACTION",
          "hard",
          { transactionId, source },
          session.player.id,
          session.installation.id,
        );
        await admin.from("pupeye_installations")
          .update({ integrity_state: "review" })
          .eq("id", session.installation.id);
        throw new HttpError(409, "DUPLICATE_TRANSACTION", "Pupeye rejected a replayed protected economy transaction.");
      }

      const { error: insertError } = await admin.from("pupeye_transactions").insert({
        transaction_id: transactionId,
        player_uuid: session.player.id,
        installation_uuid: session.installation.id,
        source,
        details,
        generation,
        request_nonce: envelope.nonce,
      });
      if (insertError) {
        if (insertError.code === "23505") {
          throw new HttpError(409, "DUPLICATE_TRANSACTION", "Pupeye rejected a duplicate protected transaction.");
        }
        throw insertError;
      }

      if (generation > Number(session.installation.max_save_generation ?? 0)) {
        await admin.from("pupeye_installations")
          .update({ max_save_generation: generation })
          .eq("id", session.installation.id);
      }

      return json(200, { ok: true, transactionId });
  } catch (error) {
    return errorResponse(error);
  }
});
