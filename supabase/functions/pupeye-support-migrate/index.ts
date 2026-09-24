import { withSupabase } from "npm:@supabase/server";
import {
  HttpError,
  audit,
  errorResponse,
  json,
  requiredString,
} from "../_shared/pupeye.ts";

export default {
  fetch: withSupabase({ auth: "secret" }, async (req, ctx) => {
    try {
      const body = await req.json();
      const action = requiredString(body.action, "action");
      const admin = ctx.supabaseAdmin;

      if (action === "approve_migration" || action === "reject_migration") {
        const migrationId = requiredString(body.migrationId, "migrationId");
        const note = typeof body.note === "string" ? body.note.slice(0, 500) : null;
        const { data: migration, error: lookupError } = await admin
          .from("pupeye_device_migrations")
          .select("*")
          .eq("id", migrationId)
          .maybeSingle();
        if (lookupError) throw lookupError;
        if (!migration) throw new HttpError(404, "MIGRATION_NOT_FOUND", "Pupeye migration request was not found.");
        if (!["pending", "approved"].includes(migration.status)) {
          throw new HttpError(409, "MIGRATION_NOT_PENDING", "This migration request can no longer be reviewed.");
        }

        const status = action === "approve_migration" ? "approved" : "rejected";
        const { error: updateError } = await admin.from("pupeye_device_migrations").update({
          status,
          reviewed_at: new Date().toISOString(),
          support_note: note,
        }).eq("id", migration.id);
        if (updateError) throw updateError;

        await audit(
          admin,
          status === "approved" ? "DEVICE_MIGRATION_APPROVED" : "DEVICE_MIGRATION_REJECTED",
          status === "approved" ? "info" : "warning",
          { migrationId, supportCode: migration.requested_support_code, note },
          migration.player_uuid,
          migration.from_installation_uuid,
        );

        return json(200, {
          ok: true,
          status,
          migrationId,
          supportCode: migration.requested_support_code,
          message: status === "approved"
            ? "Migration approved. The player can reopen Puppy Clicker on the new device to complete transfer."
            : "Migration rejected.",
        });
      }

      if (action === "clear_review") {
        const supportCode = requiredString(body.supportCode, "supportCode");
        const { data: installation, error: lookupError } = await admin
          .from("pupeye_installations")
          .select("*")
          .eq("support_code", supportCode)
          .maybeSingle();
        if (lookupError) throw lookupError;
        if (!installation) throw new HttpError(404, "INSTALLATION_NOT_FOUND", "Support Installation Code was not found.");

        const { error: updateError } = await admin.from("pupeye_installations")
          .update({ integrity_state: "clean" })
          .eq("id", installation.id);
        if (updateError) throw updateError;

        await audit(
          admin,
          "SUPPORT_REVIEW_CLEARED",
          "info",
          { supportCode },
          installation.player_uuid,
          installation.id,
        );
        return json(200, { ok: true, supportCode });
      }

      throw new HttpError(400, "SUPPORT_ACTION_INVALID", "Unsupported Pupeye support action.");
    } catch (error) {
      return errorResponse(error);
    }
  }),
};
