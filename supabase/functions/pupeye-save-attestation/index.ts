import {
  assertPublishableRequest,
  authenticateSession,
  createAdminClient,
  errorResponse,
  json,
  verifyEnvelope,
} from "../_shared/pupeye.ts";
import { HttpError } from "../_shared/http.ts";
import {
  createSupabaseAttestationStore,
  processSaveAttestation,
} from "./handler.ts";
import { validateSaveAttestationRequest } from "./policy.ts";

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json(), "save-attestation");
    const input = validateSaveAttestationRequest(envelope.payload);
    if (input.generation !== envelope.generation) {
      throw new HttpError(
        400,
        "SAVE_GENERATION_MISMATCH",
        "Save attestation generation does not match the signed PupEye request.",
      );
    }

    const admin = createAdminClient();
    const session = await authenticateSession(req, admin, envelope);
    const store = createSupabaseAttestationStore(admin, session, envelope);
    const result = await processSaveAttestation(store, input);
    return json(result.status, result.body);
  } catch (error) {
    return errorResponse(error);
  }
});
