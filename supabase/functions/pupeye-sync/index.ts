import { withSupabase } from "npm:@supabase/server";
import {
  HttpError,
  audit,
  authenticateSession,
  errorResponse,
  json,
  requiredNumber,
  requiredString,
  verifyEnvelope,
} from "../_shared/pupeye.ts";

export default {
  fetch: withSupabase({ auth: "publishable" }, async (req, ctx) => {
    try {
      const envelope = verifyEnvelope(await req.json(), "save-checkpoint");
      const admin = ctx.supabaseAdmin;
      const session = await authenticateSession(req, admin, envelope);
      const generation = requiredNumber(envelope.payload.generation, "generation");
      const supportCode = requiredString(envelope.payload.supportCode, "supportCode").slice(0, 32);
      const reason = typeof envelope.payload.reason === "string"
        ? envelope.payload.reason.slice(0, 40)
        : null;
      const hardFlags = Array.isArray(envelope.payload.hardFlags)
        ? envelope.payload.hardFlags.filter((v): v is string => typeof v === "string").slice(0, 32)
        : [];

      if (!Number.isSafeInteger(generation) || generation < 1) {
        throw new HttpError(400, "SAVE_GENERATION_INVALID", "Invalid Puppy Clicker save generation.");
      }
      if (generation < Number(session.installation.max_save_generation ?? 0)) {
        await audit(
          admin,
          "SAVE_ROLLBACK",
          "review",
          { receivedGeneration: generation, maxGeneration: session.installation.max_save_generation },
          session.player.id,
          session.installation.id,
        );
        await admin.from("pupeye_installations")
          .update({ integrity_state: "review" })
          .eq("id", session.installation.id);
        throw new HttpError(409, "SAVE_ROLLBACK", "Pupeye rejected an older save generation. Contact Support if this is a legitimate recovery.");
      }

      if (hardFlags.length > 0) {
        await audit(
          admin,
          "CLIENT_HARD_FLAGS",
          "review",
          { hardFlags },
          session.player.id,
          session.installation.id,
        );
        await admin.from("pupeye_installations")
          .update({ integrity_state: "review" })
          .eq("id", session.installation.id);
      }

      const now = new Date().toISOString();
      const { error: installError } = await admin.from("pupeye_installations").update({
        max_save_generation: generation,
        last_seen_at: now,
      }).eq("id", session.installation.id);
      if (installError) throw installError;

      const { error: headError } = await admin.from("pupeye_save_heads").upsert({
        installation_uuid: session.installation.id,
        generation,
        support_code: supportCode,
        last_reason: reason,
        hard_flags: hardFlags,
        updated_at: now,
      }, { onConflict: "installation_uuid" });
      if (headError) throw headError;

      return json(200, { ok: true, generation });
    } catch (error) {
      return errorResponse(error);
    }
  }),
};
