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
import {
  assertAttestationGeneration,
  validateSaveAttestationRequest,
} from "./policy.ts";

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json(), "save-attestation");
    const input = validateSaveAttestationRequest(envelope.payload);
    try {
      assertAttestationGeneration(input.generation, envelope.generation);
    } catch (error) {
      throw new HttpError(
        400,
        "SAVE_GENERATION_MISMATCH",
        error instanceof Error
          ? error.message
          : "Invalid Puppy Clicker save generation.",
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
