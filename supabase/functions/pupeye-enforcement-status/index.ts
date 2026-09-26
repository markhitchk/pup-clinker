import {
  assertPublishableRequest,
  createAdminClient,
  errorResponse,
  json,
  verifyEnvelope,
} from "../_shared/pupeye.ts";
import { resolveVerifiedEnforcementStatus } from "./handler.ts";

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json(), "enforcement-status");
    const admin = createAdminClient();
    const result = await resolveVerifiedEnforcementStatus(admin, envelope);
    return json(result.status, result.body);
  } catch (error) {
    return errorResponse(error);
  }
});
